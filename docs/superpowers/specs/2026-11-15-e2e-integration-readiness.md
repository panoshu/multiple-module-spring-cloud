# 全链路集成测试执行准备（批次 73）

> 生成日期：2026-11-15
> 目标：启动网关 + 双 BFF + auth/annuity/approval/file 共 7 个服务，执行端到端集成测试（对应清单 #12「端到端 HTTP 验证」的运行时部分）
> 当前状态：**被阻塞** —— 本地基础设施下线（见 §2）

---

## 1. 启动方案（已就绪）

- 脚本：`scripts/local/start-all-services.sh` —— 按依赖顺序启动 7 个服务，每服务等待端口就绪后继续，进程日志写 `/tmp/svc-<name>.log`，pid 写 `/tmp/svc-<name>.pid`
- 顺序与端口：

| 顺序 | 服务 | 端口 | 启动参数 |
|---|---|---|---|
| 1 | auth-service | 18085 | （主配置 `include: local` 自动加载本地配置） |
| 2 | file-service | 18082 | 同上 |
| 3 | approval-service | 18081 | 同上 |
| 4 | annuity-service | 18090 | `--spring.profiles.active=local`（批次 47 治理：不无条件 include local） |
| 5 | intranet-bff | 18091 | 同 auth |
| 6 | internet-bff | 18092 | `--server.port=18092`（默认 18090 与 annuity 冲突，按实例覆盖） |
| 7 | channel-gateway | 18080 | `--spring.profiles.active=dev`（dev 实例为三渠道联调入口） |

- jar 产出：8 个启动模块 pom 已加 `spring-boot.run.skip=false`（覆盖父 pom 的 true），`mvn clean install -DskipTests` 产出可执行 fat jar；integration-starter 原本已有该配置
- kernel 侧能力已随本次构建生效：Redis 分布式锁（启动期 PING fail-fast）、MDC 流程标识（`traceId/applicationId/stepExecutionId/requestNo/businessType`）随 annuity 的 logback-spring.xml 输出

## 2. 当前阻塞：基础设施下线

2026-11-15 10:16 探测结果（对比批次 72 时三者全通）：

| 依赖 | 地址 | 状态 |
|---|---|---|
| Redis | 127.0.0.1:6379 | ❌ CLOSED（auth 启动即失败：sa-token/jetcache remote 连接被拒） |
| PostgreSQL | 127.0.0.1:5432/pgdb | ❌ CLOSED |
| Nacos | 127.0.0.1:8848 | ❌ CLOSED |

- WSL 内无 redis/postgresql/nacos 安装、无 docker；Windows 宿主侧（172.22.208.1）端口同样未监听
- **需恢复原有实例**（PG 的 `schema_demo` 已含 48 张表 + 业务数据；若换新实例需重建表与数据，见 §4）

## 3. 数据库就绪状态

`schema_demo` 已建表 48 张，包括 kernel 6 张（批次 72 前由本会话执行 `business-core-infrastructure/schema-pg.sql` 建成）：

- `t_business_application` / `t_business_batch` / `t_business_form` / `t_business_step_execution` / `t_business_external_interaction` / `t_business_event_receipt`
- auth 全套 `t_auth_*`、approval 5 张、file 5 张、annuity 4 张 + `t_id_generator` + 事件存储 2 张

若恢复的是原实例则直接可用；换新实例需按各服务 `schema-pg.sql` 重放 DDL 并准备业务数据。

## 4. 全链路测试用例设计（环境恢复后执行）

| 层 | 用例 | 验证点 |
|---|---|---|
| L1 活性 | 7 个端口连通 + 各服务日志无 ERROR；权限自注册日志（`PermissionScanner` / `PermissionRegistrationRunner` 上报成功）；锁装配日志 `Initialized Lock4jDistributedLock` | 服务健康、权限项自注册、分布式锁真实生效 |
| L2 网关路由 | 经 `channel-gateway:18080` 分别路由 auth（`/auth/**`）、annuity（`/annuity/**`）、approval（`/approval/**`）、file（`/file/**`）、两个 BFF | Nacos 发现 + 路由转发 |
| L3 权限链 | auth 登录（`t_auth_user` 既有账号）拿 token → 带 token 经 intranet-bff 调 approval 查询接口 | 登录 → 会话 → `@RequirePermission` 实时校验 → 返回数据 |
| L4 业务链 | 经 internet-bff/gateway：创建批次 → 申请上传 token → 上传 Excel → 确认上传触发解析 → kernel 裂变申请单 → 自动推进至 USER_TASK 挂起 → intranet-bff 发起审批 → 审批通过 → kernel 回调推进 | kernel↔file↔approval 全链路（requestNo 幂等、回调关联、事件推进、审批人解析 SAME_PLAN/LEVEL_UP） |

前置数据需求：`t_auth_user` 可登录账号、`t_file_template_config` 活跃模板、`t_approval_flow` 匹配规则与节点（若原实例数据保留则已具备）。

## 5. 维护约定

- 环境恢复后：直接 `bash scripts/local/start-all-services.sh`，按 §4 逐层执行并在本文回填结果
- 服务停止：`for f in /tmp/svc-*.pid; do kill $(cat $f); done`
- 已知启动坑（均已修复）：annuity `httpclient5` test scope（运行时 NoClassDefFoundError）→ runtime；internet-bff 与 annuity 端口冲突 → 启动参数覆盖
