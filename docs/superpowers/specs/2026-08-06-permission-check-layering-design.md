# 权限校验分层体系设计方案

> 基于 auth-service 重新设计，废弃所有 iam-service 依赖。

## 一、设计目标

1. **消除重复**：权限切面基础设施统一提供一次，不在每个业务服务重复实现
2. **职责清晰**：认证、功能权限、业务数据权限三层分离，各归其位
3. **零侵入 kernel**：kernel 保持纯业务核心定位，不承担权限框架职责
4. **可选装配**：业务服务按需引入权限能力，不被强制绑定
5. **fail-closed**：权限服务不可达时一律拒绝，金融合规底线

## 二、现状问题清单

| 编号 | 问题 | 影响 |
|------|------|------|
| P1 | iam-service 不存在，网关依赖的 iam-api、RouteRuleApi 失效 | 网关路由规则加载失败 |
| P2 | auth-service 缺少 `/internal/permissions/check` 端点 | permission-sdk 无法调用 |
| P3 | auth-starter 模块缺失 | auth-service 无法独立启动 |
| P4 | 网关未写入 `X-Session-Context` header | kernel 的 SessionContextResolver 失效 |
| P5 | 业务服务（approval/file/integration/annuity）完全没有权限校验 | 裸奔 |
| P6 | kernel 的 `@RequireBusinessPermission` 与 permission-sdk 的 `@RequirePermission` 两套注解重复 | 概念混淆 |
| P7 | PermissionScanner 只能扫描 auth-service 自己的 Controller | 权限点元数据不完整 |

## 三、分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     客户端（携带 sa-token）                  │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  demo-gateway（认证层）                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 1. sa-token 校验 token                              │    │
│  │ 2. 渠道分派（INTERNET / HQ / BRANCH）               │    │
│  │ 3. 路由级粗粒度鉴权（从 auth-service 加载 RouteRule）│    │
│  │ 4. 写入 X-Session-Context header（性能优化，可选）   │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  业务服务（功能权限层）                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ shared-permission-starter 自动装配：                │    │
│  │   @RequirePermission AOP 切面                       │    │
│  │     ├─ 解析 (accountId, planId, business, action)   │    │
│  │     ├─ 优先读 X-Session-Context header 短路（可选）  │    │
│  │     └─ CachingPermissionClient（本地短TTL缓存）     │    │
│  │         └─ HttpPermissionClient                     │    │
│  │             → auth-service /internal/permissions/*  │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ business-core-kernel（业务数据权限层，可选）：       │    │
│  │   BusinessAccessGuard SPI                           │    │
│  │     ├─ 计划一致性 + 客户一致性                        │    │
│  │     ├─ 业务类型办理权限                              │    │
│  │     ├─ INTERNET: 代办校验                            │    │
│  │     └─ BRANCH: 二次授权校验                          │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  auth-service（权限判定引擎）                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ /internal/permissions/check        → 实时单点判定     │    │
│  │ /internal/permissions/check-batch  → 实时批量判定     │    │
│  │ /permission-cache/platform         → 平台权限快照     │    │
│  │ /permission-cache/business          → 业务权限快照     │    │
│  │ /permission-metadata/items         → 权限点元数据     │    │
│  │ /route-rules/list                  → 网关路由规则     │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ AuthorizationEngine（两层 AND + DENY 优先）          │    │
│  │ EffectivePermissionService（+ 实时角色模板解析）     │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## 四、各模块职责与改动

### 4.1 demo-gateway（认证层）

**职责**：token 校验 + 渠道分派 + 路由级粗粒度鉴权 + 会话上下文透传

**改动**：
- 依赖从 `iam-api` 改为 `auth-api`
- `RouteRuleLoader` 调用 `auth-api` 的 `RouteRuleApi`（替代 iam-api）
- 新增 `SessionContextInjector`：sa-token 校验通过后，从 Token-Session 读取会话信息，
  组装 `SessionContext` JSON，Base64 编码后写入 `X-Session-Context` 响应头
- `GatewayStpInterfaceImpl` 适配 auth-service 的权限快照接口

**不做**：不做数据级鉴权（不理解 planId/businessCode 语义）

### 4.2 auth-service（权限判定服务）

**职责**：权限判定引擎 + 对外权限能力提供方

#### 4.2.1 新增 auth-starter 模块

创建 `auth-service/auth-starter`，包含：
- 启动类 `AuthApplication`
- `application.yml` / `application-local.yml`
- 打包入口

#### 4.2.2 auth-api 新增接口

**(a) PermissionCheckApi**（实时权限判定，供 permission-sdk 调用）

```java
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {
    @GetExchange("/check")
    ApiResult<PermissionCheckResponse> check(
        @RequestParam String accountId,
        @RequestParam String planId,
        @RequestParam String businessCode,
        @RequestParam(required = false) String actionCode);

    @GetExchange("/check-batch")
    ApiResult<BatchPermissionCheckResponse> checkBatch(
        @RequestParam String accountId,
        @RequestParam String planId,
        @RequestParam String items);
}
```

响应格式适配 permission-sdk 的极简协议：
- 单点：`{"allowed": true/false}`
- 批量：`businessCode:actionCode=true,businessCode:actionCode=false`

**(b) RouteRuleApi**（网关路由规则，替代 iam-api）

```java
@HttpExchange("/route-rules")
public interface RouteRuleApi {
    @GetExchange("/list")
    ApiResult<List<RouteRuleResponse>> list(@Valid ListRouteRulesQuery query);
}
```

#### 4.2.3 auth-adapter 新增 Controller

- `PermissionCheckController` 实现 `PermissionCheckApi`，委托 `PermissionQueryService`
- `RouteRuleController` 实现 `RouteRuleApi`，委托 `RouteRuleQueryService`

#### 4.2.4 auth-domain 新增 route 限界上下文

- `domain/route/aggregate/RouteRule`（聚合根）
- `domain/route/repository/RouteRuleRepository`
- `domain/route/errorcode/RouteRuleErrorCode`
- `domain/route/event/RouteRuleUpdated` / `RouteRuleDisabled`

#### 4.2.5 auth-infrastructure 新增

- `RouteRuleDO` + `RouteRuleMapper` + `RouteRuleConverter` + `RouteRuleRepositoryImpl`
- `schema-pg.sql` / `schema-mysql.sql` 新增 `t_auth_route_rule` 表
- `PermissionScanner` 扩展为跨服务扫描（通过引入 permission-sdk 的业务服务上报，或 auth-service 提供注册 API）

### 4.3 新增 shared-permission-starter（功能权限基础设施）

**位置**：`demo-shared/shared-permission-starter`

**职责**：提供 `@RequirePermission` 注解的 AOP 切面自动装配 + `PermissionClient` 自动配置

**依赖**：
- `permission-sdk`（零依赖核心）
- `spring-boot-starter-aop`（切面）
- `spring-boot-autoconfigure`（自动装配）
- `shared-api`（ApiResult 解析）

**提供的组件**：

| 组件 | 作用 |
|------|------|
| `RequirePermissionAspect` | `@Around("@annotation(RequirePermission)")` 切面，调用 PermissionGuard.require |
| `PermissionClientAutoConfiguration` | 自动装配 HttpPermissionClient + CachingPermissionClient |
| `PlanIdResolver` SPI | 从方法入参解析 planId 的策略接口，业务服务可覆盖 |
| `AccountIdResolver` SPI | 从请求上下文解析 accountId 的策略接口 |
| `SessionContextShortCircuit`（可选） | 优先读 `X-Session-Context` header 做短路，未命中再调 auth-service |

**配置项**：

```yaml
permission:
  service:
    base-url: http://auth-service
    timeout: 2s
  cache:
    ttl: 10s
    max-size: 10000
  short-circuit:
    enabled: true  # 是否优先读 X-Session-Context header
```

**PlanIdResolver 设计**：

```java
public interface PlanIdResolver {
    String resolve(JoinPoint joinPoint);
}
```

默认实现 `DefaultPlanIdResolver`：
- 扫描方法入参，若实现 `PlanIdAware` 接口则取 `planId()`
- 若无 `PlanIdAware`，尝试从 `@RequestParam("planId")` 取
- 找不到返回 null（平台类权限不需要 planId）

业务服务可提供自定义 `PlanIdResolver` Bean 覆盖。

**AccountIdResolver 设计**：

```java
public interface AccountIdResolver {
    String resolve();
}
```

默认实现 `DefaultAccountIdResolver`：
- 从 `RequestContextHolder` 取当前请求的 `X-Account-Id` header
- 网关写入此 header（sa-token 校验通过后从 Token-Session 取）

### 4.4 business-core-kernel（业务数据权限，可选）

**职责**：提供跨业务通用的业务数据权限校验

**改动**：
- **废弃** `@RequireBusinessPermission` 注解 + `BusinessPermissionAspect`（功能权限统一走 `@RequirePermission`）
- **保留** `BusinessAccessGuard` SPI（业务数据权限：计划一致性、客户一致性、代办、二次授权）
- **保留** `SessionContextResolver`（作为 `shared-permission-starter` 的短路读 SPI 实现选项）
- kernel 内部 Controller 改用 `@RequirePermission`（通过引入 `shared-permission-starter`）

**kernel 不依赖 auth-api / permission-sdk**：保持纯业务核心定位。`BusinessAccessGuard` 的数据来源是 `SessionContext`（由网关透传或业务服务自行组装），不直接调用 auth-service。

### 4.5 业务服务（approval/file/integration/annuity）

**改动**：
- `xxx-adapter` 引入 `shared-permission-starter`
- Controller 方法标注 `@RequirePermission(business="APPROVAL", action="CREATE")`
- 请求 DTO 实现 `PlanIdAware` 接口（若涉及业务权限）
- 需要业务数据权限校验的，引入 `business-core-application` 使用 `BusinessAccessGuard`

**不做的**：
- 不在每个业务服务重复实现 AOP 切面
- 不直接依赖 `permission-sdk`（通过 `shared-permission-starter` 间接引入）
- 不直接依赖 `auth-api`（通过 SDK 的 HttpPermissionClient 调用）

## 五、权限校验调用链路（完整流程）

```
1. 客户端发送请求（携带 sa-token）
   ▼
2. demo-gateway SaReactorFilter
   ├─ ChannelAwareSaRouter.matchAndCheckLogin()  → token 校验 + 渠道分派
   ├─ RouteRuleLoader.loadRules()                → 从 auth-service 加载路由规则
   ├─ 遍历 RouteRule 做 LOGIN/PERMISSION/ROLE/CHANNEL 校验
   └─ SessionContextInjector 写入 X-Session-Context + X-Account-Id header
   ▼
3. 业务服务 Controller
   ├─ [功能权限] @RequirePermission AOP 切面触发
   │   ├─ AccountIdResolver.resolve()  → 从 X-Account-Id header 取
   │   ├─ PlanIdResolver.resolve()     → 从方法入参取（PlanIdAware）
   │   ├─ SessionContextShortCircuit   → 读 X-Session-Context header（命中则短路）
   │   └─ 未命中 → CachingPermissionClient.checkPermission()
   │       └─ HttpPermissionClient → GET auth-service/internal/permissions/check
   │           └─ PermissionCheckController → PermissionQueryService
   │               └─ EffectivePermissionService → AuthorizationEngine
   │                   ├─ 能力层校验（CAPABILITY Grant + ScopeRule）
   │                   └─ 主体层校验（Subject Grant + ScopeRule + Permission）
   │                   → 两层 AND，DENY 优先
   │
   └─ [业务数据权限] BusinessAccessGuard.checkCanHandle()（若依赖 kernel）
       ├─ 计划一致性：planNo ∈ session 可见计划
       ├─ 客户一致性：customerNo 匹配
       ├─ 业务办理权限：BUSINESS_{type}_HANDLE ∈ permissionCodes
       ├─ INTERNET 代办：planNo ∈ delegatedPlanNos
       └─ BRANCH 二次授权：hasSecondaryAuth = true
```

## 六、关键设计决策

### 6.1 为什么新建 shared-permission-starter 而不是下沉到 kernel？

| 维度 | 下沉到 kernel | 新建 shared-permission-starter |
|------|--------------|------------------------------|
| 依赖方向 | kernel 需依赖 permission-sdk + spring-aop | 业务服务依赖 starter，kernel 不变 |
| 职责单一性 | kernel 变成"业务核心 + 权限框架" | kernel 保持纯业务核心 |
| 可选性 | 所有依赖 kernel 的服务被迫接受 | 业务服务按需引入 |
| 循环依赖风险 | auth-service 若反向依赖 kernel 则循环 | 无风险 |

### 6.2 为什么废弃 kernel 的 @RequireBusinessPermission？

- 与 `@RequirePermission` 语义重复，造成概念混淆
- `permissionCodes` 集合来自网关快照，无法支持实时判定
- 统一到 `@RequirePermission` 后，权限点元数据可通过 PermissionScanner 统一扫描管理

### 6.3 为什么保留 kernel 的 BusinessAccessGuard？

- 业务数据权限（计划一致性、客户一致性、代办、二次授权）是**跨业务通用的业务规则**
- 这些校验依赖 `SessionContext`（由网关透传），不需要调用 auth-service
- 属于业务核心领域知识，归 kernel 所有符合 DDD 原则

### 6.4 X-Session-Context header 的定位

- **定位**：性能优化手段，非强依赖
- **写入方**：网关（sa-token 校验通过后）
- **读取方**：`shared-permission-starter` 的 `SessionContextShortCircuit`（可选开启）
- **兜底**：header 不存在或过期时，回退到实时调用 auth-service

### 6.5 RouteRule 迁移到 auth-service 的理由

- iam-service 已废弃，RouteRule 需要新归属
- auth-service 已管理权限体系，RouteRule 是路由级权限规则，属于权限的一部分
- 网关只需依赖 `auth-api`，不需要额外的 iam-api 模块

## 七、模块依赖关系图

```
demo-gateway
  └─ auth-api（替代 iam-api）

业务服务 xxx-adapter
  ├─ shared-permission-starter
  │   └─ permission-sdk（零依赖）
  ├─ xxx-api
  └─ business-core-application（可选，用 BusinessAccessGuard）

业务服务 xxx-application
  └─ business-core-application（可选）

auth-service
  ├─ auth-starter（新建，启动入口）
  ├─ auth-adapter → auth-api
  ├─ auth-application → auth-domain
  └─ auth-infrastructure → auth-domain

demo-shared/shared-permission-starter（新建）
  ├─ permission-sdk
  ├─ spring-boot-starter-aop
  └─ spring-boot-autoconfigure
```

## 八、实施步骤（按依赖顺序）

### 阶段一：auth-service 权限能力补齐

| 步骤 | 内容 | 模块 |
|------|------|------|
| 1 | 创建 auth-starter 模块（启动类 + 配置） | auth-service/auth-starter |
| 2 | auth-api 新增 PermissionCheckApi | auth-service/auth-api |
| 3 | auth-adapter 新增 PermissionCheckController | auth-service/auth-adapter |
| 4 | auth-domain 新增 route 限界上下文 | auth-service/auth-domain |
| 5 | auth-infrastructure 新增 RouteRule 持久化 | auth-service/auth-infrastructure |
| 6 | auth-api 新增 RouteRuleApi | auth-service/auth-api |
| 7 | auth-adapter 新增 RouteRuleController | auth-service/auth-adapter |
| 8 | schema-pg/sql 新增 t_auth_route_rule 表 | auth-service/auth-infrastructure |

### 阶段二：shared-permission-starter 创建

| 步骤 | 内容 |
|------|------|
| 9 | 创建 demo-shared/shared-permission-starter 模块 |
| 10 | 实现 RequirePermissionAspect 切面 |
| 11 | 实现 PermissionClientAutoConfiguration |
| 12 | 实现 PlanIdResolver / AccountIdResolver SPI |
| 13 | 实现 SessionContextShortCircuit（可选） |
| 14 | 编写单元测试 |

### 阶段三：网关改造

| 步骤 | 内容 |
|------|------|
| 15 | pom.xml 依赖 iam-api → auth-api |
| 16 | RouteRuleLoader 适配 auth-api 的 RouteRuleApi |
| 17 | 新增 SessionContextInjector |
| 18 | GatewayStpInterfaceImpl 适配 auth-service 权限快照 |

### 阶段四：kernel 改造

| 步骤 | 内容 |
|------|------|
| 19 | 废弃 @RequireBusinessPermission + BusinessPermissionAspect |
| 20 | kernel 内部 Controller 改用 @RequirePermission |
| 21 | kernel 引入 shared-permission-starter |

### 阶段五：业务服务接入

| 步骤 | 内容 |
|------|------|
| 22 | 各业务服务 xxx-adapter 引入 shared-permission-starter |
| 23 | Controller 标注 @RequirePermission |
| 24 | 请求 DTO 实现 PlanIdAware（若涉及业务权限） |
| 25 | 需要业务数据权限的引入 business-core-application |

### 阶段六：验证

| 步骤 | 内容 |
|------|------|
| 26 | auth-service 独立启动验证 |
| 27 | 端到端权限校验链路测试 |
| 28 | 全量回归测试 |

## 九、PlanIdAware 接口约定

```java
// 位于 shared-permission-starter
public interface PlanIdAware {
    String planId();
}
```

业务服务的请求 DTO 实现此接口，切面通过它解析 planId：

```java
public class CreateApprovalFlowRequest implements PlanIdAware {
    private String planId;
    private String flowName;
    // ...
    @Override
    public String planId() { return planId; }
}
```

## 十、降级策略

| 场景 | 行为 |
|------|------|
| auth-service 不可达 | fail-closed，PermissionGuard 抛 PermissionDeniedException |
| X-Session-Context header 不存在 | 回退到实时调用 auth-service |
| CachingPermissionClient 缓存未命中 | 回退到 HttpPermissionClient 实时调用 |
| 业务服务未引入 shared-permission-starter | 无功能权限校验（需评估风险） |
| 业务服务未引入 business-core-application | 无业务数据权限校验（需评估风险） |

## 十一、与现有代码的兼容性

| 现有组件 | 处理方式 |
|---------|---------|
| permission-sdk | 保留，零依赖核心不变 |
| auth-domain AuthorizationEngine | 保留，权限判定引擎不变 |
| auth-application PermissionQueryService | 保留，新增 PermissionCheckApi 调用入口 |
| kernel BusinessAccessGuard | 保留，业务数据权限 SPI 不变 |
| kernel SessionContextResolver | 保留，作为 shared-permission-starter 短路读实现 |
| kernel @RequireBusinessPermission | **废弃**，统一到 @RequirePermission |
| kernel BusinessPermissionAspect | **废弃**，由 shared-permission-starter 的切面替代 |
| 网关 iam-api 依赖 | **替换**为 auth-api |
| 网关 RouteRuleLoader | **改造**，调用 auth-api 的 RouteRuleApi |
