# API 链路测试 curl 命令

> 本文档提供通过网关（端口 18080）访问各服务的 curl 测试命令，覆盖以下链路：
> - 网关 → annuity-service（健康检查）
> - 网关 → annuity-service → approval-service（跨服务调用审批流）
> - 网关 → annuity-service → file-service（跨服务调用文件服务）
> - 网关 → annuity-service → integration-service（跨服务调用外接服务接口）
> - 网关 → approval-service（直接调用审批流 API）
> - 网关 → file-service（直接调用文件服务 API）
> - 网关 → integration-service（直接调用外接服务 API）

## 服务端口规划

| 服务                | 端口  | Nacos 服务名     |
|---------------------|-------|------------------|
| demo-gateway        | 18080 | gateway          |
| approval-service    | 18081 | approval-service |
| file-service        | 18082 | file-service     |
| integration-service | 18083 | integration      |
| annuity-service     | 18090 | annuity-service  |

## 启动顺序

1. demo-gateway（网关）
2. approval-service / file-service / integration-service（基础服务，可并行启动）
3. annuity-service（业务服务，依赖 approval/file/integration 的 API）

---

## 1. 网关 → annuity-service（健康检查）

验证网关路由和 annuity-service 服务连通性。

```bash
curl -X POST http://localhost:18080/annuity/api/annuity/test/health \
  -H "Content-Type: application/json"
```

**预期响应**：返回 `ApiResult<HealthResponse>`，包含 service、status、timestamp 字段。

---

## 2. 网关 → annuity-service → approval-service（跨服务调用审批流）

验证 annuity-service 通过 @HttpExchange 客户端调用 approval-service 的链路。

```bash
curl -X POST http://localhost:18080/annuity/api/annuity/test/link-approval \
  -H "Content-Type: application/json" \
  -d '{"approver": "test-user"}'
```

**预期响应**：返回 `ApiResult<Object>`，data 为 approval-service 的待审批列表查询结果（即使列表为空，只要返回了 ApiResult
结构即说明链路连通）。

---

## 3. 网关 → annuity-service → file-service（跨服务调用文件服务）

验证 annuity-service 通过 @HttpExchange 客户端调用 file-service 的链路。

```bash
curl -X POST http://localhost:18080/annuity/api/annuity/test/link-file \
  -H "Content-Type: application/json" \
  -d '{"fileTaskId": "test-task-001"}'
```

**预期响应**：返回 `ApiResult<Object>`，data 为 file-service 的文件任务查询结果。

---

## 4. 网关 → annuity-service → integration-service（跨服务调用外接服务接口）

验证 annuity-service 通过 @HttpExchange 客户端调用 integration-service 的链路。

```bash
curl -X POST http://localhost:18080/annuity/api/annuity/test/link-integration \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "WEB",
    "tellerNo": "T001",
    "tellerName": "测试柜员",
    "enterpriseCustomerNo": "C0000001",
    "enterprisePlanNo": "P0000001",
    "annuityProductNo": "AP001"
  }'
```

**预期响应**：返回 `ApiResult<Object>`，data 为 integration-service 的投资组合余额查询结果。

---

## 5. 网关 → approval-service（直接调用现有 API：创建审批流）

验证网关到 approval-service 的直接路由和审批流创建接口。

```bash
curl -X POST http://localhost:18080/approval/api/approval/flows/create \
  -H "Content-Type: application/json" \
  -d '{
    "flowName": "测试审批流",
    "businessType": "ANNUITY",
    "matchRules": {
      "accountManagerCodes": ["M001"],
      "businessTypes": ["ANNUITY"],
      "amountMin": 0,
      "amountMax": 1000000
    },
    "nodes": [
      {
        "nodeName": "初审",
        "nodeType": "APPROVAL",
        "approvalRole": "MANAGER",
        "approvalUsers": ["M001"],
        "order": 1,
        "required": true
      }
    ],
    "createdBy": "test-user"
  }'
```

**预期响应**：返回 `ApiResult<ApprovalFlowIdResponse>`，包含生成的 flowId。

---

## 6. 网关 → file-service（直接调用现有 API：查询文件任务）

验证网关到 file-service 的直接路由和文件任务查询接口。

```bash
curl -X POST http://localhost:18080/file/api/file/tasks/get \
  -H "Content-Type: application/json" \
  -d '{"fileTaskId": "test-task-001"}'
```

**预期响应**：返回 `ApiResult<FileTaskDTO>`（若任务不存在则 data 为 null，但 ApiResult 结构正常返回）。

---

## 7. 网关 → integration-service（直接调用现有 API：查询投资组合余额）

验证网关到 integration-service 的直接路由和外接服务查询接口。

```bash
curl -X POST http://localhost:18080/integration/api/v1/trade/portfolio/balance \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "WEB",
    "tellerNo": "T001",
    "tellerName": "测试柜员",
    "enterpriseCustomerNo": "C0000001",
    "enterprisePlanNo": "P0000001",
    "annuityProductNo": "AP001",
    "pageRequest": {
      "startPos": 0,
      "pageSize": 10
    }
  }'
```

**预期响应**：返回 `ApiResult<PortfolioBalanceDTO>`，包含投资组合余额分页数据。

---

## 链路覆盖矩阵

| 编号 | 链路                         | 验证点                                                 |
|------|------------------------------|--------------------------------------------------------|
| 1    | 网关 → annuity               | 网关路由配置 + annuity 服务启动                        |
| 2    | 网关 → annuity → approval    | @HttpExchange 客户端 + LoadBalancer + approval 服务    |
| 3    | 网关 → annuity → file        | @HttpExchange 客户端 + LoadBalancer + file 服务        |
| 4    | 网关 → annuity → integration | @HttpExchange 客户端 + LoadBalancer + integration 服务 |
| 5    | 网关 → approval              | 网关路由 + approval 服务直接访问                       |
| 6    | 网关 → file                  | 网关路由 + file 服务直接访问                           |
| 7    | 网关 → integration           | 网关路由 + integration 服务直接访问                    |

## 测试说明

- 命令 2/3/4 的返回数据来自下游服务，即使下游查不到数据（如 fileTaskId 不存在），只要返回了 `ApiResult` 结构（code/message/data
  字段）即说明链路连通。
- 命令 5 会真实创建审批流记录，重复执行会生成多条数据。
- 所有命令通过网关（18080）访问，网关通过 StripPrefix=1 去除路径前缀后转发到对应服务。
