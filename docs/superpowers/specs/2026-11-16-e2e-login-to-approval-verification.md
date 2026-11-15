# 全链路验证指南：登录 → 选计划 → 建批次 → 文件解析 → 推进 → 审批

> 适用环境：本地 8 服务（auth/file/approval/annuity/integration/双 BFF/gateway）+ PostgreSQL + Redis + Nacos + RocketMQ
> 验证日期：2026-11-16
> 关联文档：`docs/superpowers/specs/2026-11-15-e2e-integration-readiness.md`、`docs/superpowers/plans/2026-11-15-architecture-repair-plan.md`

本文给出一条**可复制执行**的本地全链路验证路径，覆盖：

1. 网关登录（auth-service）
2. 查询/选择计划（auth-service，会话声明落 Redis）
3. 创建业务批次 + 空表单（annuity-service，经 kernel）
4. 申请文件上传 token（annuity-service → file-service）
5. 上传 Excel（file-service）
6. 确认上传 → 文件解析 → 批次裂变出申请单 → 自动推进到 APPROVAL
7. 手动推进审批步骤（annuity kernel → approval-service）
8. 审批人登录并审批通过（approval-service）
9. 审批结果事件回传 → 申请单 COMPLETED → 批次对账 COMPLETED
10. 用 SQL 核对每一步落库结果

---

## 0. 前置条件与启动

### 0.1 基础设施

| 组件 | 地址 | 说明 |
|---|---|---|
| PostgreSQL | `127.0.0.1:5432/pgdb`，schema `schema_demo` | 用户 `user_demo` / 密码 `passwd_demo` |
| Redis | `127.0.0.1:6379` | sa-token 会话 + 事件回滚通道 |
| Nacos | `127.0.0.1:8848` | 服务注册 |
| RocketMQ NameServer | `127.0.0.1:9876` | Docker |
| RocketMQ Proxy(gRPC) | `127.0.0.1:8081` | 项目事件通道，topic `event` |

### 0.2 启动 8 个服务

```bash
cd /home/archie/develop/java/multiple-module-spring-cloud
bash scripts/local/start-all-services.sh
```

脚本按依赖顺序启动并等待端口：

| 服务 | 端口 |
|---|---|
| channel-gateway | 18080 |
| approval-service | 18081 |
| file-service | 18082 |
| integration-service | 18083 |
| auth-service | 18085 |
| annuity-service | 18090 |
| intranet-bff | 18091 |
| internet-bff | 18092 |

日志在 `/tmp/svc-<name>.log`，pid 在 `/tmp/svc-<name>.pid`。全部 JVM 参数统一
`-Xms128m -Xmx256m -XX:+UseG1GC --enable-preview`。

### 0.3 RocketMQ 检查（最容易出问题的一步）

```bash
docker ps --format '{{.Names}} {{.Status}}' | sort
docker exec rmqproxy sh -c \
  '/home/rocketmq/rocketmq-5.3.2/bin/mqadmin topicRoute -n rmqnamesrv:9876 -t event'
```

若 `rmqbroker` 未运行或 `topicRoute` 报 `No topic route info`：

```bash
docker start rmqbroker
docker restart rmqproxy          # broker 重启后 proxy 必须重启，否则负缓存持续 40402
sleep 6
docker exec rmqproxy sh -c \
  '/home/rocketmq/rocketmq-5.3.2/bin/mqadmin topicRoute -n rmqnamesrv:9876 -t event'
```

若 topic 不存在：

```bash
docker exec rmqproxy sh -c \
  '/home/rocketmq/rocketmq-5.3.2/bin/mqadmin updateTopic -n rmqnamesrv:9876 -c DefaultCluster -t event -r 8 -w 8'
docker restart rmqproxy
```

> 本地已给三个 RMQ 容器设置 `restart=unless-stopped`；若仍反复退出，先看
> `docker logs --tail 100 rmqbroker`，不要直接重启业务服务。

### 0.4 本地权限开关

`start-all-services.sh` 对业务服务显式传 `--permission.enforcement.enabled=false`，
这是**仅限本地**的演示开关；生产必须保持权限强制开启。

---

## 1. 数据库种子数据

### 1.1 一键执行

```bash
cd /home/archie/develop/java/multiple-module-spring-cloud
docker exec -i -e PGPASSWORD=passwd_demo postgres psql \
  -h 127.0.0.1 -U user_demo -d pgdb -v ON_ERROR_STOP=1 \
  < scripts/local/seed-e2e-data.sql
```

脚本幂等，可重复执行。内容与对应表如下：

| 数据 | 表 | 关键值 |
|---|---|---|
| 经办人账号 | `t_auth_user` | `U1001`，状态 ACTIVE |
| 审批人账号 | `t_auth_user` | `U-APPROVER`，状态 ACTIVE |
| 密码凭证 | `t_auth_credential` | `cred-demo-0001` / `cred-demo-approver`，密码 `Test123456` |
| 计划权限 | `t_auth_assignment` | U1001、U-APPROVER → `AGENT_REVIEW` / `PLAN` / `P-1` |
| 客户渠道开通 | `t_auth_customer_channel_entitlement` | `C-1` 开通 `["NETAPP"]` |
| 审批流 | `t_approval_flow` | `ACC_PLAN_CREATE`，`accountManager=CJP` |
| 审批节点 | `t_approval_node` | 第 1 节点，指定审批人 `["U-APPROVER"]` |

### 1.2 计划快照来自配置文件，不是数据库

`auth-service` 本地配置 `application-local.yml` 中：

```yaml
auth:
  org-directory:
    enabled: true
    plans:
      - plan-no: P-1
        product-no: PROD-1
        customer-no: C-1
        name: 联调测试计划
```

`P-1` 的客户/产品维度由 `InMemoryProductGateway` 提供。若删掉这段配置，登录成功但
`selectable-plans` 必然为空。

### 1.3 文件模板由 file-service 自动播种

file-service 本地 profile 开启 `file.demo.template-seed.enabled=true`，启动时幂等插入
`FORM_DETAIL` 模板。验证：

```sql
SELECT id, biz_type, template_version, status
FROM schema_demo.t_file_template_config
WHERE biz_type = 'FORM_DETAIL' AND status = 'ACTIVE';
```

> 若换成非 local profile，需要自行准备 `t_file_template_config`（或通过管理端导入），
> 否则确认上传后解析任务会因找不到模板失败。

---

## 2. 手工验证步骤（可直接复制）

先定义 shell 变量（后续命令都依赖）：

```bash
BASE=http://127.0.0.1:18080
FILE_BASE=http://127.0.0.1:18082

psql_demo() {
  docker exec -e PGPASSWORD=passwd_demo postgres \
    psql -h 127.0.0.1 -U user_demo -d pgdb "$@"
}
```

### 步骤 1：登录（网关 → auth）

```bash
TOKEN=$(curl -sS -X POST "$BASE/internet/auth/login" \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: verify-login-001' \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "USER",
    "ownerId": "U1001",
    "channel": "01",
    "proof": "Test123456"
  }' | jq -er '.data.token')

AUTH="satoken-internet: $TOKEN"
echo "$TOKEN"
```

请求说明：

| 字段 | 值 / 说明 |
|---|---|
| `channel` | CodeEnum 编码：`01`=NETAPP、`02`=TELLER、`05`=BANK_BRANCH；也接受 `NETAPP` 等名称 |
| `credentialType` | `PASSWORD` |
| `ownerType` | `USER` |
| `proof` | 明文密码；DB 中保存 BCrypt |
| 返回 header | `satoken-internet` 为后续请求的 token 名 |

预期响应：

```json
{"code":"COMMON.0000","message":"success","data":{"token":"...","tokenName":"satoken-internet","accountId":"U1001","channel":"NETAPP"}}
```

### 步骤 2：查询可选计划并选择 P-1

```bash
# 可选计划：应返回 ["P-1"]
curl -sS -X POST "$BASE/internet/auth/selectable-plans" -H "$AUTH" | jq

# 选择计划
curl -sS -X POST "$BASE/internet/auth/select-plan" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"planId":"P-1"}' | jq
```

预期：

```json
{"code":"COMMON.0000","message":"success","data":{"plans":["P-1"]},...}
{"code":"COMMON.0000","message":"success","data":null,...}
```

选择计划后，auth 会向 sa-token Token-Session 写入/合并 `sessionClaims`，包括
`planNo=P-1`、`customerNo=C-1`、`productNo=PROD-1`、`planName=联调测试计划`。
**必须完成本步骤再做后续文件 token**，否则 kernel 拿不到 customer/product，file-service
会报 `accessScope 不能为空`。

### 步骤 3：创建业务批次 + 空表单（annuity legacy 入口）

> 为什么用 `/annuity/api/annuity/upload`：当前 BFF `batch/create` 的场景类型是
> `ANNUITY_OPEN`，而年金领域枚举是 `ACC_PLAN_CREATE`，两者尚未做契约映射；
> legacy 入口一次完成“批次+表单”创建，且请求体携带完整业务上下文，适合本地验证。
> BFF 的查询接口仍然可用（见第 5 节）。

```bash
CREATE_RESP=$(curl -sS -X POST "$BASE/annuity/api/annuity/upload" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -H 'X-Trace-Id: verify-create-001' \
  -d '{
    "customerNo": "C-1",
    "productNo": "PROD-1",
    "planNo": "P-1",
    "businessType": "ACC_PLAN_CREATE",
    "operationModel": "03",
    "accountManager": "CJP",
    "operatorId": "U1001",
    "operatorName": "演示用户",
    "channel": "NETAPP",
    "fileName": "annuity_form_detail.xlsx",
    "fileSize": 3687,
    "planType": "NEW",
    "initialContribution": 10000,
    "hasForeignInvestment": false
  }')

echo "$CREATE_RESP" | jq
BATCH_ID=$(echo "$CREATE_RESP" | jq -er '.data.batchId')
FORM_ID=$(echo "$CREATE_RESP" | jq -er '.data.formId')
echo "batch=$BATCH_ID form=$FORM_ID"
```

字段约束：

| 字段 | 约束 |
|---|---|
| `businessType` | `ACC_PLAN_CREATE`（新建）；另有 MODIFY / DELETE |
| `operationModel` | CodeEnum：`03`=受托+账管（`01` 单受托、`02` 单账管） |
| `accountManager` | 枚举名：`CJP`/`CMB`/`BOC`/`ICBC`/...；必须与审批流 `match_rules.accountManager` 一致 |
| `planType` | `NEW`/`MODIFY`/`DELETE`；新建用 `NEW` |
| `initialContribution` | 新建默认 10000（分值）；不能为负 |
| `fileSize` | 必须等于实际上传文件字节数（示例文件 3687） |
| `channel` | `NETAPP`（或 `01`） |

预期：

```json
{"code":"COMMON.0000","data":{"batchId":"20261116000002","batchStatus":"CREATED","formId":"2026111601...","formStatus":"WAITING_UPLOAD",...}}
```

### 步骤 4：申请文件上传 token（BFF → annuity → file-service）

```bash
TOKEN_RESP=$(curl -sS -X POST "$BASE/internet/bff/form/upload-token" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{
    \"businessType\": \"ANNUITY_OPEN\",
    \"batchId\": \"$BATCH_ID\",
    \"fileName\": \"annuity_form_detail.xlsx\",
    \"fileSize\": 3687,
    \"contentType\": \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\"
  }")

echo "$TOKEN_RESP" | jq
FILE_TOKEN=$(echo "$TOKEN_RESP" | jq -er '.data.token')
```

说明：

- `businessType` 这里只用于 BFF 场景路由，传 `ANNUITY_OPEN`（BFF 支持
  `ANNUITY_OPEN`/`ANNUITY_CHANGE`）；实际批次业务类型仍是上一步的 `ACC_PLAN_CREATE`。
- token 的访问范围来自**会话声明** `C-1/PROD-1`，由步骤 2 选择计划后写入。
- 返回 `data.token`；若 `data` 为 `null`，先检查步骤 2 是否成功、Redis 会话声明是否含
  `customerNo/productNo`（第 7 节有命令）。

### 步骤 5：上传 Excel 到 file-service

```bash
UPLOAD_RESP=$(curl -sS -X POST "$FILE_BASE/api/file/access/upload" \
  -H "X-File-Token: $FILE_TOKEN" \
  -H 'X-User-No: U1001' \
  -H 'X-Customer-No: C-1' \
  -H 'X-Product-No: PROD-1' \
  -F "file=@docs/excel/annuity_form_detail.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

echo "$UPLOAD_RESP" | jq
FILE_ID=$(echo "$UPLOAD_RESP" | jq -er '.fileId.value')
echo "fileId=$FILE_ID"
```

三个 `X-*-No` header 是 file-service 本地会话约定，缺一个会返回
`SERVICE.FILE.0032 会话 Header 缺失`。示例文件路径相对仓库根目录。

预期：

```json
{"fileId":{"value":"2026111601..."},"originalName":"annuity_form_detail.xlsx","size":3687,"digest":null}
```

### 步骤 6：确认上传 → 触发解析 / 裂变 / 自动推进

```bash
curl -sS -X POST "$BASE/internet/annuity/core/form/confirm-upload" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -H 'X-Trace-Id: verify-confirm-001' \
  -d "{
    \"batchId\": \"$BATCH_ID\",
    \"formId\": \"$FORM_ID\",
    \"fileId\": \"$FILE_ID\",
    \"fileName\": \"annuity_form_detail.xlsx\"
  }" | jq
```

预期 `COMMON.0000`。后台链路：

```
confirm-upload
  → BusinessForm.confirmUpload
  → file-service 解析 FORM_DETAIL
  → FileParsedEventDTO 经 RocketMQ topic event
  → kernel FileParsedEventListener
  → 创建 BusinessApplication（每个 subTask 一个）
  → 自动推进：FORM_DETAIL_INGESTION → DATA_VERIFICATION → MATERIAL_PREPARATION → APPROVAL（挂起）
```

轮询申请单列表（通常 2~10 秒）：

```bash
APPLICATION_ID=""
for i in $(seq 1 30); do
  APPLICATION_ID=$(curl -sS -X POST "$BASE/internet/annuity/core/application/list" \
    -H 'Content-Type: application/json' -H "$AUTH" \
    -d "{\"batchId\":\"$BATCH_ID\"}" | jq -r '.data[0].applicationId // empty')
  [ -n "$APPLICATION_ID" ] && break
  sleep 2
done
echo "applicationId=$APPLICATION_ID"
```

查看状态（此时应为 `RUNNING` / `APPROVAL`）：

```bash
curl -sS -X POST "$BASE/internet/annuity/core/application/detail" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{\"applicationId\":\"$APPLICATION_ID\"}" | jq
```

### 步骤 7：推进到审批（创建审批实例）

```bash
REQUEST_NO="SE-VERIFY-$(date +%s)"
curl -sS -X POST "$BASE/internet/annuity/core/application/advance" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -H 'X-Trace-Id: verify-advance-001' \
  -d "{
    \"applicationId\": \"$APPLICATION_ID\",
    \"expectedStep\": \"APPROVAL\",
    \"requestNo\": \"$REQUEST_NO\"
  }" | jq
```

预期：

```json
{"code":"COMMON.0000","data":{"applicationId":"...","nextStep":"APPROVAL","status":"WAITING_EXTERNAL"}}
```

> `expectedStep` / `requestNo` 是 kernel 推进契约的必填字段；`expectedStep` 不一致会被
> 拒绝（防过期请求），`requestNo` 用于跨服务幂等。当前 controller 使用应用服务的
> 推进入口，但仍会执行 `@Valid` 校验。

查询审批实例 ID（当前没有直接返回，用 SQL 最直观）：

```bash
INSTANCE_ID=$(psql_demo -Atc \
  "select id from schema_demo.t_approval_instance where business_application_id='$APPLICATION_ID';" \
  | tr -d '[:space:]')
echo "approvalInstanceId=$INSTANCE_ID"
```

此时 `t_approval_instance.status = APPROVING`，审批人为种子数据里的 `U-APPROVER`。

### 步骤 8：审批人登录并审批通过

```bash
APPROVER_TOKEN=$(curl -sS -X POST "$BASE/internet/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "USER",
    "ownerId": "U-APPROVER",
    "channel": "01",
    "proof": "Test123456"
  }' | jq -er '.data.token')

APPROVE_BODY=$(jq -nc --argjson id "$INSTANCE_ID" '{
  instanceId: {value: $id},
  approver: "U-APPROVER",
  comment: "全链路验证通过"
}')

curl -sS -X POST "$BASE/approval/api/approval/instances/approve" \
  -H 'Content-Type: application/json' \
  -H "satoken-internet: $APPROVER_TOKEN" \
  -H 'X-Trace-Id: verify-approve-001' \
  -d "$APPROVE_BODY" | jq
```

说明：

- 路径是网关 `/approval/**` 路由（StripPrefix 后到 approval-service `/api/approval/instances/...`）。
- `approver` 字段服务端会忽略，实际审批人取自网关签名的 `X-Account-Id`；但字段仍要求非空。
- 如果返回 `SERVICE.APPROVAL.0011 不是当前节点的审批人`，说明登录账号与审批流
  `t_approval_node.approver_ids` 不一致。

### 步骤 9：等待完成与批次对账

审批通过后，approval-service 发布 `ApprovalInstanceApprovedEventDTO`，annuity 的 kernel
监听器执行 `completeWaitingStep`：

```bash
# 轮询申请单
for i in $(seq 1 30); do
  STATUS=$(curl -sS -X POST "$BASE/internet/annuity/core/application/detail" \
    -H 'Content-Type: application/json' -H "$AUTH" \
    -d "{\"applicationId\":\"$APPLICATION_ID\"}" | jq -r '.data.status')
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 2
done
echo "application.status=$STATUS"

# 批次状态由 60s 对账任务收敛（COMPLETED + successCount=1）
psql_demo -c "select id,status,total_application_count,success_count,failed_count
              from schema_demo.t_business_batch where id='$BATCH_ID';"
```

预期：

```
application: COMPLETED / current_step=COMPLETED
approval instance: APPROVED
batch: COMPLETED / total=1 / success=1 / failed=0
```

---

## 3. 数据库验收 SQL（按阶段）

把 `BATCH_ID` / `FORM_ID` / `FILE_ID` / `APPLICATION_ID` / `INSTANCE_ID` 换成上一步的值。

| 阶段 | 表 | 验收点 |
|---|---|---|
| 登录/选计划 | `t_auth_user`、`t_auth_credential`、`t_auth_assignment`、`t_auth_customer_channel_entitlement` | 账号 ACTIVE、凭证 ACTIVE、PLAN=P-1、C-1 开通 NETAPP |
| 创建批次/表单 | `t_business_batch`、`t_business_form` | 批次 CREATED；表单 WAITING_UPLOAD |
| 上传 | `t_file_metadata`、`t_file_access_log` | 文件记录大小 3687、状态 AVAILABLE；有上传流水 |
| 解析 | `t_file_parse_task`、`t_file_sub_task_data` | 状态 SUCCESS，total=2 / valid=2 / invalid=0 |
| 裂变/推进 | `t_business_application`、`t_business_step_execution` | 自动推进到 APPROVAL，步骤 1~4 SUCCEEDED |
| 审批 | `t_approval_instance`、`t_approval_node_execution`、`t_approval_record` | APPROVING→APPROVED；记录 U-APPROVER/APPROVE |
| 完成 | `t_business_application`、`t_business_batch`、`t_annuity_employee_batch`、`t_annuity_employee_detail` | 申请/批次 COMPLETED；员工 2/2 |
| 事件 | `sys_event_store`、`sys_event_dispatch_log` | 领域/集成事件已落库，RocketMQ 投递 SUCCESS |

可直接执行的汇总查询：

```bash
psql_demo -c "select id,status,total_application_count,success_count,failed_count
              from schema_demo.t_business_batch where id='$BATCH_ID';" \
  -c "select id,form_status,form_file_id from schema_demo.t_business_form where id='$FORM_ID';" \
  -c "select id,original_name,size,status,business_batch_id
       from schema_demo.t_file_metadata where id='$FILE_ID';" \
  -c "select id,status,total_rows,valid_count,invalid_count,request_no
       from schema_demo.t_file_parse_task where source_file_id='$FILE_ID';" \
  -c "select id,status,current_step from schema_demo.t_business_application where id='$APPLICATION_ID';" \
  -c "select id,step_code,status from schema_demo.t_business_step_execution
       where application_id='$APPLICATION_ID' order by create_time;" \
  -c "select id,status,request_no from schema_demo.t_approval_instance where id='$INSTANCE_ID';" \
  -c "select execution_id,approver_id,action,opinion from schema_demo.t_approval_record
       where execution_id in (select id from schema_demo.t_approval_node_execution
       where instance_id='$INSTANCE_ID');" \
  -c "select id,batch_status,total_employee_count,processed_count
       from schema_demo.t_annuity_employee_batch where application_id='$APPLICATION_ID';"
```

---

## 4. 实测记录（2026-11-16，P-1 流程）

| 项目 | 值 |
|---|---|
| 批次 | `20261116000002` |
| 表单 | `2026111601M7FZBKMVK2NRC6PWPD2PCV6M`（PARSED） |
| 文件 | `2026111601M7FZBQZFATNQ8TNV7ESBH5CD`（3687 bytes） |
| 申请单 | `2026111601M7FZBR7NPR9TM4W2956A7STK`（COMPLETED/COMPLETED） |
| 审批实例 | `61902894068137984`（APPROVED） |
| 审批记录 | `U-APPROVER` / `APPROVE` |
| 解析任务 | SUCCESS，total=2 / valid=2 / invalid=0 |
| 员工批次 | 2 / 2，明细 2 行（MATERIAL_READY） |
| 批次对账 | COMPLETED / total=1 / success=1 / failed=0 |

---

## 5. BFF 查询接口（可选，验证读模型）

登录 token 与 `businessType=ANNUITY_OPEN`（BFF 场景类型）：

```bash
# 批次详情
curl -sS -X POST "$BASE/internet/bff/batch/detail" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{\"businessType\":\"ANNUITY_OPEN\",\"batchId\":\"$BATCH_ID\"}" | jq

# 批次聚合（批次详情 + 进度 + 申请单列表）
curl -sS -X POST "$BASE/internet/bff/dashboard/batch-overview" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{\"businessType\":\"ANNUITY_OPEN\",\"batchId\":\"$BATCH_ID\"}" | jq

# 申请单详情
curl -sS -X POST "$BASE/internet/bff/application/detail" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{\"businessType\":\"ANNUITY_OPEN\",\"applicationId\":\"$APPLICATION_ID\"}" | jq

# 材料清单
curl -sS -X POST "$BASE/internet/bff/material/list" \
  -H 'Content-Type: application/json' -H "$AUTH" \
  -d "{\"businessType\":\"ANNUITY_OPEN\",\"applicationId\":\"$APPLICATION_ID\"}" | jq
```

---

## 6. 日志与链路验证

每个请求都可以带 `X-Trace-Id`；网关会透传，servlet 服务写入 MDC：

```bash
curl -sS -D - -o /dev/null -X POST "$BASE/internet/auth/login" \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: verify-trace-001' \
  -d '{"credentialType":"PASSWORD","ownerType":"USER","ownerId":"U1001","channel":"01","proof":"Test123456"}'
```

响应头应包含：

```
X-Trace-Id: verify-trace-001
trace-id: verify-trace-001
```

日志检索：

```bash
grep 'verify-trace-001' /tmp/svc-auth.log /tmp/svc-annuity.log
grep "applicationId:$APPLICATION_ID" /tmp/svc-annuity.log | head
```

annuity 日志格式：

```
[traceId:...][applicationId:...][stepExecutionId:...][requestNo:SE-...][businessType:ACC_PLAN_CREATE]
```

---

## 7. 常见问题排查

### 7.1 `selectable-plans` 返回空数组

1. 检查 `t_auth_customer_channel_entitlement` 是否有 `C-1` 且 `enabled_channels` 含
   `NETAPP`（本仓库已修复 converter：同时兼容 `NETAPP` 和 `01` 两种存储格式）。
2. 检查 auth 本地配置 `auth.org-directory.enabled=true` 且 `plans` 含 `P-1`。
3. 检查 `t_auth_assignment` 中 U1001 的 PLAN 范围是 `P-1` 且 ACTIVE。

### 7.2 上传 token 返回 `data: null` / `accessScope 不能为空`

说明会话声明里没有 `customerNo/productNo`：

```bash
docker exec redis redis-cli GET \
  "satoken-internet:internet:token-session:$TOKEN" \
  | jq -r '.dataMap.sessionClaims' | jq
```

正常应含：

```json
{"userNo":"U1001","channelType":"INTERNET","revision":2,"planNo":"P-1",
 "customerNo":"C-1","productNo":"PROD-1","planName":"联调测试计划"}
```

若只有 `planNo`，确认是否执行了步骤 2 的 `select-plan`，以及 auth 是否已重启到最新 jar。

### 7.3 确认上传后没有生成申请单

```bash
tail -80 /tmp/svc-file.log | grep -E "解析|ERROR|No topic route"
tail -80 /tmp/svc-annuity.log | grep -E "FileParsed|解析" 
```

最常见原因是 RocketMQ broker/proxy 未就绪（`No topic route info ... event`），按 §0.3
重启 broker + proxy；文件已上传成功时，重发一次 confirm-upload 或等待事件重投即可。

### 7.4 审批返回 `不是当前节点的审批人`

```sql
select n.approver_ids, f.match_rules
from schema_demo.t_approval_node n
join schema_demo.t_approval_flow f on f.id = n.flow_id
where f.business_type = 'ACC_PLAN_CREATE' and f.status = 'ACTIVE';
```

审批账号必须是 `approver_ids` 中的人（种子数据为 `U-APPROVER`）。

### 7.5 批次一直 PROCESSING

申请单 COMPLETED 后，批次由 `BatchReconciliationJob`（默认 60s）重算。等待一轮后复查；
若仍不动，检查 annuity 是否有调度线程日志和 `t_business_application` 的终态。

### 7.6 BFF `batch/create` 返回无权限 / 业务类型错误

当前 BFF 场景类型（`ANNUITY_OPEN`）与年金领域枚举（`ACC_PLAN_CREATE`）尚未做契约映射，
且 kernel `BusinessAccessGuard` 需要会话 `permissionCodes` 含
`BUSINESS_ANNUITY_OPEN_HANDLE`。本地验证请使用 §2 步骤 3 的 legacy 上传入口；
BFF 的查询/聚合接口不受影响。

---

## 8. 重复执行与清理

- 每次执行可用新的 `planNo`（如 `PLAN-VERIFY-$(date +%s)`）或固定 `P-1`；批次 ID 由 ID
  服务生成，不会冲突。
- 本次验证产生的批次/申请/审批/文件记录保留在 `schema_demo`，便于复盘；如需清理，按
  `batch_id` / `application_id` 级联删除对应业务表（生产禁止直接删）。
- 种子 SQL 可重复执行，不会重置已有业务数据，只会把账号/凭证/权限/审批流恢复到可用状态。
