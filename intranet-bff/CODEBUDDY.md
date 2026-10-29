# intranet-bff CODEBUDDY.md

> 本文档由 `/legacy-explain` 自动生成，供 AI 代理快速理解内网/专线 BFF 的架构与实现。

## 1. 模块定位

`intranet-bff` 是**内网/专线渠道**的 BFF（Backend For Frontend）层。与极简的 `internet-bff`（仅 3 个 Java 文件）不同，`intranet-bff` 承载了**管理后台**的全部职能：

- **业务操作**：复用 `bff-shared` 的策略模式 + 并发聚合（与 internet-bff 完全一致）
- **权限管理**：透明代理 auth-service 的权限校验、元数据、缓存 API
- **渠道管理**：透明代理 auth-service 的客户渠道开通 API
- **审批流管理**：透明代理 approval-service 的审批流模板 CRUD
- **审批实例管理**：透明代理 approval-service 的审批实例操作 + 查询
- **系统信息**：本地逻辑，读取 Spring Environment 返回运行时信息

渠道标识：`bff.channel-scope: INTRANET`

## 2. 架构概览

```
┌──────────────────────────────────────────────────────────────────────┐
│                        intranet-bff-adapter                          │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│  │ Business     │ │ Permission   │ │ Channel      │ │ System     │ │
│  │ Controller   │ │ Controller   │ │ Controller   │ │ Controller │ │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └─────┬──────┘ │
│         │                │                │               │         │
│  ┌──────┴───────┐ ┌──────┴───────┐ ┌──────┴───────┐             │
│  │ ApprovalFlow │ │ ApprovalInst │ │              │             │
│  │ Controller   │ │ Controller   │ │              │             │
│  └──────────────┘ └──────────────┘ │              │             │
└─────────┬──────────────┬───────────┴──────┬───────┴─────────────┬─┘
          │              │                  │                     │
   ┌──────┴──────┐ ┌─────┴──────┐ ┌────────┴──────┐  ┌───────────┴──────┐
   │ bff-shared  │ │Permission   │ │Channel        │  │System            │
   │ (BffBusiness│ │Management   │ │Management     │  │Management        │
   │  Service /  │ │Service      │ │Service        │  │Service           │
   │  Aggregation│ │→ auth-svc   │ │→ auth-svc     │  │(local logic)     │
   │  Service)   │ │  APIs       │ │  API          │  │                  │
   └─────────────┘ └─────────────┘ └───────────────┘  └──────────────────┘
```

## 3. 分层结构（5 层，无 types/domain）

```
intranet-bff/
├── intranet-bff-api          # API 接口 + DTO
├── intranet-bff-application  # 应用服务（4 个）
├── intranet-bff-adapter      # 控制器（6 个）
├── intranet-bff-infrastructure # 基础设施（当前为空壳）
└── intranet-bff-starter      # 启动模块
```

> BFF 无 domain 层，不含业务规则；所有业务逻辑由后端微服务承担。

## 4. API 层

### 4.1 接口清单

| 接口 | 路径前缀 | 端点数 | 代理目标 |
|------|----------|--------|----------|
| `BffBusinessApi` (bff-shared) | `/bff` | 5 | annuity-service (via bff-shared strategy) |
| `BffPermissionApi` | `/management/permissions` | 7 | auth-service |
| `BffChannelApi` | `/management/channels` | 4 | auth-service |
| `BffApprovalFlowApi` | `/management/approval/flows` | 6 | approval-service |
| `BffApprovalInstanceQueryApi` | `/management/approval/instances` | 3 | approval-service |
| `BffApprovalInstanceOperationApi` | `/management/approval/instances` | 5 | approval-service |
| `BffSystemApi` | `/management/system` | 2 | 本地逻辑 |

### 4.2 ISP 接口隔离

审批实例 API 被拆分为两个接口：

- **`BffApprovalInstanceQueryApi`** — 3 个只读端点：`get`、`my-pending`、`history`
- **`BffApprovalInstanceOperationApi`** — 5 个写操作端点：`start`、`approve`、`reject`、`transfer`、`withdraw`

两者由同一个 `BffApprovalInstanceController` 实现，但接口隔离便于后续按角色授权。

### 4.3 BFF 特有 DTO

| DTO | 字段 | 用途 |
|-----|------|------|
| `BffSystemInfoResponse` | channelScope, serviceName, port, contextPath | 系统运行时信息 |
| `BffBusinessTypeResponse` | businessType, serviceName, channelScope | 支持的业务类型列表（当前返回空） |

## 5. 应用层

### 5.1 透明代理服务（3 个）

| 服务 | 注入的 API 代理 | 方法数 |
|------|----------------|--------|
| `PermissionManagementService` | PermissionCheckApi, PermissionMetadataApi, PermissionCacheApi | 7 |
| `ChannelManagementService` | CustomerChannelEntitlementApi | 4 |
| `ApprovalManagementService` | ApprovalFlowApi, ApprovalInstanceApi | 14 (6+8) |

所有方法均为**一行转发**，不做任何数据变换、校验或聚合。

### 5.2 本地逻辑服务（1 个）

**`SystemManagementService`**

- `getInfo()` — 从 `Environment` 读取 `bff.channel-scope`、`spring.application.name`、`server.port`、`server.servlet.context-path`，构造 `BffSystemInfoResponse`
- `listBusinessTypes()` — 当前返回空列表，附 TODO 注释：
  > 重构后由各 BusinessScenarioHandler 声明自身支持的业务类型，通过 ScenarioHandlerRegistry 汇总，而非从路由配置表查询。该方法将在 Task #5 完成 Strategy 基础设施后重新实现。

## 6. 适配器层

6 个控制器，严格实现 API 层定义的接口：

| 控制器 | 实现接口 | 注入的服务 |
|--------|----------|-----------|
| `BffBusinessController` | `BffBusinessApi` (bff-shared) | BffBusinessService, BffAggregationService |
| `BffPermissionController` | `BffPermissionApi` | PermissionManagementService |
| `BffChannelController` | `BffChannelApi` | ChannelManagementService |
| `BffApprovalFlowController` | `BffApprovalFlowApi` | ApprovalManagementService |
| `BffApprovalInstanceController` | `BffApprovalInstanceOperationApi` + `BffApprovalInstanceQueryApi` | ApprovalManagementService |
| `BffSystemController` | `BffSystemApi` | SystemManagementService |

> `BffApprovalInstanceController` 同时实现两个 ISP 接口，是唯一一个多接口实现的控制器。

## 7. 基础设施层

当前 `intranet-bff-infrastructure` 是**空壳**：

- `pom.xml` 为 0 字节
- 无 Java 源文件
- 测试资源中仅有 `schema-h2.sql`（定义了 `t_bff_route_config` 表，但当前无代码使用该表）
- 测试配置 `application.yml` 配置了 H2 数据源 + MyBatis-Flex

### 7.1 路由配置表（预留）

```sql
CREATE TABLE IF NOT EXISTS t_bff_route_config (
  id            BIGINT PRIMARY KEY,
  business_type VARCHAR(64) NOT NULL,
  service_name  VARCHAR(128) NOT NULL,
  channel_scope VARCHAR(32) NOT NULL DEFAULT 'ALL',
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  description   VARCHAR(256),
  created_by    VARCHAR(64),
  create_time   TIMESTAMP,
  updated_by    VARCHAR(64),
  update_time   TIMESTAMP,
  deleted       BOOLEAN NOT NULL DEFAULT FALSE,
  version       INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bff_route_business_channel
  ON t_bff_route_config(business_type, channel_scope, deleted);
```

该表与 internet-bff 完全相同，但 `SystemManagementService.listBusinessTypes()` 尚未实现从此表读取。

## 8. 启动模块

### 8.1 应用类

```java
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
    "com.example.bff.intranet",   // 本模块
    "com.example.bff.shared"      // bff-shared 共享库
})
public class IntranetBffApplication { ... }
```

### 8.2 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | 18091 | 内网 BFF 端口 |
| `server.servlet.context-path` | `/intranet-bff` | 上下文路径 |
| `bff.channel-scope` | `INTRANET` | 渠道标识 |
| `management.tracing.propagation.type` | `W3C` | 链路追踪传播格式 |
| `management.observation.traces.output.zipkin` | `host=${ZIPKIN_HOST:localhost}` | Zipkin 地址 |

### 8.3 HttpExchange 客户端路由（local 环境）

| 基包 | 目标服务 |
|------|----------|
| `com.example.approval.api` | `lb://approval-service` |
| `com.example.auth.api` | `lb://auth-service` |
| `com.example.annuity.api.kernel` | `lb://annuity-service` |

## 9. 测试覆盖

### 9.1 单元测试（adapter 层）

| 测试类 | 覆盖端点 | 注释 |
|--------|----------|------|
| `BffBusinessControllerTest` | 4 (createBatch, batchDetail, submitApplication, batchOverview) | 与 internet-bff 测试一致 |
| `BffPermissionControllerTest` | 2 (check, listItems) | 注释"其余 5 个方法同模式" |
| `BffChannelControllerTest` | 4 (enable, get, disable, replace) | 全覆盖 |
| `BffApprovalControllerTest` | 12 (6 flow + 6 instance) | 覆盖 flow create/list/get/update/deprecate/match + instance start/approve/reject/transfer/withdraw/my-pending/history |
| `BffSystemControllerTest` | 2 (info, business-types) | 全覆盖 |

### 9.2 集成测试（starter 层）

`IntranetBffApplicationTest` — `@SpringBootTest`，验证 6 个控制器 Bean 均已注册。外部 `@HttpExchange` API 通过 `@MockitoBean` 注入空实现。

### 9.3 测试基础设施

- **H2 内存数据库**（PostgreSQL 兼容模式）
- **TestApplication** — adapter 测试专用 `@SpringBootApplication`，提供独立的 Spring 上下文入口
- starter 集成测试排除了 Nacos 自动配置和 `WebAutoConfiguration`（因缺少 hibernate-validator）

## 10. 与 internet-bff 的对比

| 维度 | internet-bff | intranet-bff |
|------|-------------|-------------|
| 端口 | 18090 | 18091 |
| 上下文路径 | `/internet-bff` | `/intranet-bff` |
| 渠道标识 | `INTERNET` | `INTRANET` |
| 自有 Java 文件数 | 3 (1 controller + 1 app + 1 test) | ~20 (6 controllers + 4 services + 6 API + 2 DTO + 1 app + 1 test) |
| 管理类 API | 无 | 权限、渠道、审批流、审批实例 |
| 本地逻辑 | 无 | SystemManagementService |
| bff-shared 复用 | 完全一致 | 完全一致 |
| HttpExchange 客户端 | annuity-kernel | annuity-kernel + approval + auth |
| starter 集成测试排除项 | 无 | 排除 Nacos + WebAutoConfiguration |

## 11. 请求流程

### 11.1 透明代理请求（以权限校验为例）

```
前端 → POST /intranet-bff/management/permissions/check
  → BffPermissionController.check(request)
    → PermissionManagementService.check(request)
      → PermissionCheckApi.check(request)  [Retrofit HTTP 调用 → auth-service]
  ← ApiResult<PermissionCheckResponse>
```

### 11.2 业务操作请求（与 internet-bff 一致）

```
前端 → POST /intranet-bff/bff/dashboard/batch-overview
  → BffBusinessController.batchOverview(request)
    → BffAggregationService.getBatchOverview(request)
      → ScenarioHandlerRegistry.handlerFor(businessType)
        → AnnuityScenarioHandler.getBatchOverview(request)
          → CompletableFuture 并发聚合
  ← ApiResult<BatchOverviewResponse>
```

### 11.3 系统信息请求（本地逻辑）

```
前端 → POST /intranet-bff/management/system/info
  → BffSystemController.getInfo()
    → SystemManagementService.getInfo()
      → 读取 Environment 属性
  ← ApiResult<BffSystemInfoResponse>
```

## 12. 待确认问题

1. **`listBusinessTypes()` 何时实现？** — TODO 注释指向 "Task #5"，但无具体计划时间线。当前返回空列表。
2. **`t_bff_route_config` 表是否仍需要？** — DDL 已定义但无代码使用。是遗留设计还是待实现？
3. **`intranet-bff-infrastructure` 为何是空壳？** — 是否有计划将路由配置、缓存等逻辑从 bff-shared 迁移到此？
4. **为什么 intranet-bff 的集成测试需要排除 `WebAutoConfiguration`？** — 注释说缺少 hibernate-validator，但 internet-bff 不需要排除。两 BFF 的依赖差异在哪？
5. **审批实例的 ISP 拆分是否有授权意义？** — Query 和 Operation 接口拆分后，是否计划在网关层按接口做不同鉴权规则？
6. **`BffApprovalInstanceController` 同时实现两个接口** — 未来是否考虑拆成两个控制器？
7. **管理类 API 全部一行转发，BFF 层的价值在哪？** — 除了统一入口和未来可扩展性，当前是否考虑过直接暴露后端服务？
8. **`PermissionManagementService` 注入 3 个 auth-service API** — 为什么不直接注入 auth-service 的统一 facade API？这是 auth-service API 设计的粒度选择还是历史原因？
9. **trace context mapping 中 OrderId/UserId/BizId/BatchId** — 这些自定义 trace tag 在什么场景下使用？是日志关联还是链路追踪关联？
10. **intranet-bff 与 internet-bff 共享 `bff-shared` 的策略模式** — 当两种渠道的业务逻辑出现分化时（如 internet 需要代理委派检查，intranet 不需要），策略模式如何适配渠道差异？

## 13. 关键文件索引

| 文件 | 说明 |
|------|------|
| `intranet-bff-api/.../BffPermissionApi.java` | 权限管理 API（7 端点） |
| `intranet-bff-api/.../BffChannelApi.java` | 渠道管理 API（4 端点） |
| `intranet-bff-api/.../BffApprovalFlowApi.java` | 审批流管理 API（6 端点） |
| `intranet-bff-api/.../BffApprovalInstanceQueryApi.java` | 审批实例查询 API（3 端点） |
| `intranet-bff-api/.../BffApprovalInstanceOperationApi.java` | 审批实例操作 API（5 端点） |
| `intranet-bff-api/.../BffSystemApi.java` | 系统信息 API（2 端点） |
| `intranet-bff-application/.../PermissionManagementService.java` | 权限管理透明代理 |
| `intranet-bff-application/.../ChannelManagementService.java` | 渠道管理透明代理 |
| `intranet-bff-application/.../ApprovalManagementService.java` | 审批管理透明代理（14 方法） |
| `intranet-bff-application/.../SystemManagementService.java` | 系统信息本地逻辑 |
| `intranet-bff-adapter/.../BffApprovalInstanceController.java` | 唯一多接口实现的控制器 |
| `intranet-bff-starter/.../IntranetBffApplication.java` | 启动类 |
| `intranet-bff-starter/src/main/resources/application.yml` | 主配置 |
| `intranet-bff-starter/src/test/resources/application.yml` | 测试配置（含排除列表） |

## 14. 扩展指南：新增管理类 API

1. 在 `intranet-bff-api` 中定义 `@HttpExchange` 接口 + DTO
2. 在 `intranet-bff-application` 中创建 `XxxManagementService`，注入目标微服务的 `@HttpExchange` API 代理
3. 在 `intranet-bff-adapter` 中创建控制器实现 API 接口
4. 在 `application-local.yml` 中配置 HttpExchange 客户端基包 → 目标服务映射
5. 如需本地逻辑，在 `SystemManagementService` 中扩展或创建新服务
6. 补充 `@WebMvcTest` 单元测试 + `IntranetBffApplicationTest` 中的 Bean 注册验证
