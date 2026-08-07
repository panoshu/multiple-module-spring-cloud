# 统一鉴权体系设计

> 日期：2026-08-07
> 主题：把业务类功能与管理类功能统一纳入鉴权体系，支持垂直越权与水平越权防护

## 一、背景与目标

### 1.1 现状分析

当前体系已具备的能力：

| 能力 | 实现机制 |
|------|---------|
| 网关层渠道登录校验 | `ChannelAwareSaRouter` 对 `/internet/**`、`/hq/**`、`/branch/**` 路径调用对应 `StpLogic.checkLogin()` |
| 功能权限校验（垂直越权防护） | `@RequirePermission` 注解 + `RequirePermissionAspect` 切面 → `PermissionCheckApi.check()` |
| 范围匹配（部分水平越权防护） | `ScopeMatcher.matches(scopeRules, plan)` 在 `checkPermission` 时判定 |
| DENY 优先 + 白名单语义 | `EffectResolver` 命中 DENY 即拒绝，都未命中默认拒绝 |
| 内部接口防护 | `ExcludeRouteFilter` 对 `/internal/**` 返回 403 |
| 白名单豁免 | `SaReactorFilter.addExclude` 硬编码豁免路径 |

当前体系存在的 4 个核心缺口：

| 缺口 | 影响 | 根因 |
|------|------|------|
| 非渠道前缀路径在网关层直接放行 | `/admin/**` 等系统管理 API 无登录校验 | `SaTokenGatewayConfiguration.setAuth` 中 `channel == null` 时直接 `return` |
| `auth.gateway.public-paths` 配置与代码割裂 | 修改 yml 不生效 | `addExclude` 硬编码，未消费 `GatewayProperties` |
| auth-service 自身 API 无功能权限校验 | 任何登录用户都能调用渠道开通、权限元数据查询等敏感操作 | 避免循环调用而未标注 `@RequirePermission` |
| kernel 权限点未被 PermissionScanner 注册 | `t_auth_permission_item` 表缺少 kernel 权限点元数据 | `PermissionScanner` 只扫描 auth-service 本地 Controller |
| 列表查询缺少行级数据过滤 | 用户可能看到非授权范围的数据行 | 仅有 `ScopeMatcher.matches` 单点判定，无批量聚合能力 |
| `RouteRuleController` 返回空列表 | RouteRule 机制事实废弃 | 设计的 5 种 checkType 与新分层体系冲突 |
| 测试接口进入生产 | `AnnuityLinkTestController` 无任何校验 | 缺少环境隔离 |

### 1.2 设计目标

1. 把 API 划分为业务类功能（BUSINESS，与计划/产品/客户绑定）和管理类功能（PLATFORM，与具体计划无关），都纳入鉴权体系
2. 支持垂直越权防护（用户访问无权限的功能）
3. 支持水平越权防护（用户访问非授权范围的数据，含行级数据过滤）
4. 消除当前体系的所有安全缺口
5. 复用现有 `scopeRules`，不新增"数据可见范围"配置属性

### 1.3 关键决策（已与用户确认）

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 管理类 API 范围 | auth-service 自身 API + 未来系统管理后台 API + 业务服务 PLATFORM 类 API | 统一纳入 PLATFORM 类别 |
| 水平越权防护层次 | 含行级数据过滤 | 完整防护 |
| auth-service 自身防护 | 切面内部短路调用 `EffectivePermissionService` | 避免 HttpExchange 循环 |
| 行级过滤实现机制 | AOP + 注解 + Repository 配合（方案 C） | 平衡自动化与可控性 |
| kernel 权限点注册 | 业务服务启动时上报到 auth-service | 统一元数据来源 |
| 网关路由防护 | 所有路径都校验登录 + yml 白名单 | 简化 RouteRule |
| RouteRule 去留 | 移除 | yml 白名单 + 所有路径校验登录已覆盖 |
| 垂直越权防护严格度 | 仅功能权限判定，不引入渠道-路径绑定 | 保持灵活 |
| 数据可见范围是否新增属性 | 不新增，复用现有 `scopeRules` | 配置一致性，零迁移成本 |

## 二、总体架构

### 2.1 鉴权分层

```
客户端请求
  ↓
demo-gateway（SaReactorFilter）
  ├─ yml 白名单（public-paths）→ 直接放行
  ├─ 渠道前缀路径（/internet/** /hq/** /branch/**）→ 对应渠道 StpLogic.checkLogin()
  └─ 非渠道前缀路径（/admin/** 等）→ 默认 StpLogic.checkLogin()（识别任一渠道 token）
  ↓
SessionContextInjector
  └─ 从已登录渠道 Token-Session 读取 → 注入 X-Account-Id（签名）
  ↓
业务服务 / auth-service Controller
  ├─ @RequirePermission（功能权限 + 垂直越权防护）
  │   └─ RequirePermissionAspect → PermissionExecutor.check()
  │       ├─ 业务服务：HttpExchangePermissionExecutor → PermissionCheckApi.check()
  │       └─ auth-service：LocalPermissionExecutor → 本地 PermissionQueryService（短路）
  └─ @DataScope（行级数据过滤 + 水平越权防护）
      └─ DataScopeAspect → DataScopeResolver.resolve() → DataScope
          └─ Repository 拼接 QueryWrapper 条件
```

### 2.2 API 分类与处理方式

| API 分类 | 示例 | 功能权限 | 数据过滤 | 网关层 |
|---------|------|---------|---------|--------|
| 业务类 | `/internet/annuity/upload`、`/business/application/list` | `@RequirePermission(BUSINESS)` | `@DataScope` | 渠道登录校验 |
| 管理类 | `/admin/users/list`、`/permission-metadata/items` | `@RequirePermission(PLATFORM)` | 不需要 | 通用登录校验 |
| 登录类 | `/internet/auth/login`、`/admin/auth/login` | 豁免 | 不需要 | yml 白名单放行 |
| 内部类 | `/internal/permissions/check` | 不适用 | 不需要 | `ExcludeRouteFilter` 403 |

### 2.3 两层防护

| 防护类型 | 实现机制 | 防护对象 |
|---------|---------|---------|
| 垂直越权 | `@RequirePermission` 注解 + `PermissionExecutor.check()` | 功能访问权限（用户能否调用此 API） |
| 水平越权 | `@DataScope` 注解 + `DataScopeResolver.resolve()` + Repository 条件拼接 | 数据可见范围（用户能看到哪些数据行） |

### 2.4 关键设计原则

1. 复用现有 `scopeRules`：不新增"数据可见范围"配置属性，复用 Grant/Assignment/RoleTemplate 已有的 `scopeRules`
2. 单点判定 + 批量聚合：现有 `ScopeMatcher.matches`（单点）继续用于功能权限判定；新增 `resolveDataScope`（批量）用于行级过滤
3. fail-closed：所有异常情况都拒绝访问，包括 `resolveDataScope` 失败
4. 管理类 API 不走数据过滤：PLATFORM 类权限不绑定 plan，无需 `@DataScope`
5. RouteRule 移除：网关层改为"所有路径都校验登录 + yml 白名单"

## 三、网关层改造

### 3.1 改造目标

修复当前网关层的两个核心缺口：
1. 非渠道前缀路径（如 `/admin/**`）直接放行，不做登录校验
2. `auth.gateway.public-paths` 配置与代码割裂，修改 yml 不生效

### 3.2 SaTokenGatewayConfiguration.setAuth 改造

```java
.setAuth(obj -> {
    String path = SaHolder.getRequest().getRequestPath();
    
    // 1. yml 白名单放行（从 GatewayProperties 读取，替代硬编码 addExclude）
    if (gatewayProperties.isPublicPath(path)) {
        return;
    }
    
    // 2. 渠道前缀路径 → 对应渠道 StpLogic 登录校验
    ChannelType channel = channelAwareSaRouter.matchChannel(path);
    if (channel != null) {
        channelAwareSaRouter.getStpLogic(channel).checkLogin();
        return;
    }
    
    // 3. 非渠道前缀路径 → 默认 StpLogic 登录校验
    //    覆盖 /admin/**、/permission-metadata/** 等管理类 API
    StpUtil.checkLogin();
})
```

关键变化：
- 删除 `channel == null` 时的 `return`，改为调用 `StpUtil.checkLogin()` 做通用登录校验
- 管理类 API 的用户可能通过任一渠道登录，统一用默认 StpLogic 校验

### 3.3 默认 StpLogic 的渠道 Token 识别

在 `ChannelAwareSaRouter` 中新增方法，配置默认 StpLogic 识别所有渠道 token：

```java
// 配置默认 StpLogic 识别所有渠道 token
public void configureDefaultStpLogic() {
    StpLogic defaultLogic = new StpLogic("default");
    SaTokenConfig config = new SaTokenConfig();
    // 默认 StpLogic 读取所有三个渠道的 Header
    config.setTokenName(List.of(
        ChannelType.INTERNET.headerName(),
        ChannelType.HQ.headerName(),
        ChannelType.BRANCH.headerName()
    ));
    defaultLogic.setConfig(config);
    StpUtil.setStpLogic(defaultLogic);
}
```

效果：管理类 API 请求携带任一渠道 token，默认 StpLogic 都能识别并校验登录态。

### 3.4 移除 addExclude 硬编码

```java
// 删除原有 addExclude 硬编码
// .addExclude("/actuator/**", "/internet/auth/login", ...)
// 改为在 setAuth 内通过 gatewayProperties.isPublicPath(path) 判断
```

### 3.5 新增 GatewayProperties 配置类

```java
@ConfigurationProperties(prefix = "auth.gateway")
public class GatewayProperties {
    private List<String> publicPaths = List.of();
    
    public boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(pattern -> 
            new AntPathMatcher().match(pattern, path));
    }
}
```

### 3.6 application.yml 配置调整

```yaml
auth:
  gateway:
    # 公共白名单（无需登录校验）—— 被代码消费，替代硬编码
    public-paths:
      - /actuator/**
      - /actuator
      - /favicon.ico
      - /internet/auth/login
      - /hq/auth/login
      - /branch/auth/login
      - /branch/auth/secondary-auth/initiate
      - /branch/auth/secondary-auth/confirm
      - /branch/auth/secondary-auth/status/**
      # 管理后台登录接口
      - /admin/auth/login
```

### 3.7 SessionContextInjector 扩展

当前 `SessionContextInjector` 只对渠道前缀路径注入 X-Account-Id。扩展支持非渠道前缀路径：

```java
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    
    // 1. 白名单路径不注入
    if (gatewayProperties.isPublicPath(path)) {
        return chain.filter(exchange);
    }
    
    // 2. 尝试从任一已登录渠道获取 loginId
    String loginId = resolveAnyChannelLoginId();
    if (loginId == null) {
        return chain.filter(exchange);  // 未登录，由 SaReactorFilter 拦截
    }
    
    // 3. 注入 X-Account-Id（签名）+ X-Session-Context
    return injectHeaders(exchange, loginId, path).then(chain.filter(exchange));
}

private String resolveAnyChannelLoginId() {
    for (ChannelType channel : ChannelType.values()) {
        StpLogic stpLogic = channelAwareSaRouter.getStpLogic(channel);
        try {
            if (stpLogic.isLogin()) {
                return stpLogic.getLoginIdAsString();
            }
        } catch (Exception ignored) {
            // 该渠道未登录，继续尝试下一个
        }
    }
    return null;
}
```

### 3.8 RouteRule 相关代码移除

| 移除项 | 文件 |
|--------|------|
| RouteRuleLoader | `demo-gateway/.../RouteRuleLoader.java` |
| RouteRule 值对象 | `demo-gateway/.../RouteRule.java` |
| RouteRuleTest | `demo-gateway/src/test/.../RouteRuleTest.java` |
| RouteRuleApi | `auth-api/.../RouteRuleApi.java` |
| RouteRuleController | `auth-adapter/.../RouteRuleController.java` |
| RouteRuleResponse | `auth-api/dto/RouteRuleResponse.java` |
| httpexchange 客户端配置 | `application.yml` 中 `io.github.danielliu1123.httpexchange.clients` |

### 3.9 改造后的鉴权流程示例

**场景：访问 `/admin/users/list`（管理类 API）**

1. SaReactorFilter 拦截
2. `gatewayProperties.isPublicPath("/admin/users/list")` → false，非白名单
3. `channelAwareSaRouter.matchChannel("/admin/users/list")` → null，非渠道前缀
4. `StpUtil.checkLogin()` → 默认 StpLogic 检查任一渠道 token → 已登录（HQ 渠道）→ 通过
5. SessionContextInjector 注入 X-Account-Id（从 HQ 渠道 Token-Session 获取）
6. 路由到 auth-service 的 UserController.list
7. `@RequirePermission(business="USER", action="VIEW", category=PLATFORM)` 触发切面
8. `PermissionExecutor.check()` → auth-service 内短路调用 → `checkPlatformPermission()` → allowed=true
9. 返回用户列表

## 四、auth-service 内部短路调用机制

### 4.1 问题背景

当前 `RequirePermissionAspect` 通过 HttpExchange 调用 `PermissionCheckApi.check()`。如果 auth-service 的 Controller 也标注 `@RequirePermission`，会形成循环调用：

```
auth-service Controller → @RequirePermission 切面 → HttpExchange 调用 auth-service 自身 → 又触发 @RequirePermission 切面 → 死循环
```

### 4.2 解决方案：PermissionExecutor 接口抽象

在 shared-permission-starter 中定义抽象接口，auth-service 提供本地实现：

```java
// shared-permission-starter 中定义
public interface PermissionExecutor {
    /**
     * 执行权限校验，返回是否允许。
     * 实现可以是 HttpExchange 远程调用，也可以是本地直接调用。
     */
    PermissionCheckResult check(PermissionCheckContext context);
    
    /** 是否支持本地短路调用（auth-service 实现返回 true） */
    default boolean isLocalExecution() { return false; }
}
```

### 4.3 HttpExchangePermissionExecutor（默认实现，业务服务用）

```java
@Component
@ConditionalOnMissingBean(PermissionExecutor.class)
public class HttpExchangePermissionExecutor implements PermissionExecutor {
    private final PermissionCheckApi permissionCheckApi;
    
    @Override
    public PermissionCheckResult check(PermissionCheckContext context) {
        ApiResult<PermissionCheckResponse> result = permissionCheckApi.check(
            new PermissionCheckRequest(
                context.accountId(),
                context.planId(),
                context.businessCode(),
                context.actionCode()));
        return PermissionCheckResult.from(result);
    }
    
    @Override
    public boolean isLocalExecution() { return false; }
}
```

### 4.4 LocalPermissionExecutor（auth-service 专用）

```java
// 位置：auth-adapter
@Component
@Primary  // 覆盖默认的 HttpExchangePermissionExecutor
public class LocalPermissionExecutor implements PermissionExecutor {
    private final PermissionQueryService permissionQueryService;
    
    @Override
    public PermissionCheckResult check(PermissionCheckContext context) {
        CheckPermissionQuery query = new CheckPermissionQuery(
            UserNo.of(context.accountId()),
            resolvePlanNo(context.planId()),
            new BusinessCode(context.businessCode()),
            resolveActionCode(context.actionCode()));
        boolean allowed = permissionQueryService.checkPermission(query);
        return new PermissionCheckResult(allowed, null);
    }
    
    @Override
    public boolean isLocalExecution() { return true; }
}
```

### 4.5 RequirePermissionAspect 改造

不再直接依赖 `PermissionCheckApi`，改为依赖 `PermissionExecutor` 接口：

```java
@Around("@annotation(requirePermission)")
public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
        throws Throwable {
    String accountId = accountIdResolver.resolve(joinPoint);
    if (accountId == null || accountId.isBlank()) {
        throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
            .withLogDetail("X-Account-Id header 缺失或验签失败");
    }
    
    String planId = planIdResolver.resolve(joinPoint, requirePermission);
    String businessCode = requirePermission.business();
    String actionCode = requirePermission.action().isBlank() ? null : requirePermission.action();
    
    PermissionCheckContext context = new PermissionCheckContext(
        accountId, planId, businessCode, actionCode);
    
    PermissionCheckResult result;
    try {
        result = permissionExecutor.check(context);
    } catch (Exception e) {
        log.warn("[RequirePermission] 权限校验失败, fail-closed. account={}, business={}",
            accountId, businessCode, e);
        throw new BusinessException(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE, e)
            .withContext("account", accountId)
            .withContext("business", businessCode);
    }
    
    if (!result.allowed()) {
        throw new BusinessException(PermissionErrorCode.PERMISSION_DENIED)
            .withContext("account", accountId)
            .withContext("plan", planId)
            .withContext("business", businessCode)
            .withContext("action", actionCode);
    }
    return joinPoint.proceed();
}
```

关键变化：通过 `@Primary` 让 auth-service 的 `LocalPermissionExecutor` 覆盖默认实现。同一份 `@RequirePermission` 注解和切面代码，在 auth-service 中走本地，在业务服务中走远程，业务代码无感知。

### 4.6 auth-service Controller 标注清单

短路机制建立后，auth-service 的 Controller 可以安全标注 `@RequirePermission`：

| Controller | 方法 | business | action | category |
|-----------|------|----------|--------|----------|
| CustomerChannelEntitlementController | enable | CHANNEL_ENTITLEMENT | ENABLE | PLATFORM |
| CustomerChannelEntitlementController | disable | CHANNEL_ENTITLEMENT | DISABLE | PLATFORM |
| CustomerChannelEntitlementController | replace | CHANNEL_ENTITLEMENT | REPLACE | PLATFORM |
| CustomerChannelEntitlementController | get | CHANNEL_ENTITLEMENT | VIEW | PLATFORM |
| PermissionMetadataController | listItems | PERMISSION_METADATA | VIEW | PLATFORM |
| PermissionMetadataController | listGroupedItems | PERMISSION_METADATA | VIEW | PLATFORM |
| PermissionCacheController | getPlatformPermissions | PERMISSION_CACHE | VIEW | PLATFORM |
| PermissionCacheController | getBusinessPermissions | PERMISSION_CACHE | VIEW | PLATFORM |
| PermissionRegistrationController | register | — | — | **不标注**（内部接口） |
| PermissionCheckController | check / checkBatch | — | — | **不标注**（内部接口） |

### 4.7 依赖调整

auth-adapter 的 pom.xml 新增 shared-permission-starter 依赖，以获取 `@RequirePermission` 切面：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>shared-permission-starter</artifactId>
</dependency>
```

不需要引入 auth-api 的 `PermissionCheckApi`，因为短路调用不通过 HttpExchange，`LocalPermissionExecutor` 直接依赖 auth-application 的 `PermissionQueryService`。

### 4.8 循环依赖检查

```
auth-adapter (Controller) 
  → shared-permission-starter (RequirePermissionAspect + PermissionExecutor 接口)
  → auth-application (PermissionQueryService)
  → auth-domain (EffectivePermissionService)
```

无循环依赖：
- auth-adapter 依赖 shared-permission-starter（切面）
- auth-adapter 依赖 auth-application（LocalPermissionExecutor 调用 PermissionQueryService）
- shared-permission-starter 不依赖 auth-application（通过 PermissionExecutor 接口抽象）

## 五、行级数据过滤实现

### 5.1 设计目标

为业务类 API（BUSINESS 类别）提供行级数据过滤能力，防止水平越权。核心原则：复用现有 `scopeRules`，不新增配置属性。

### 5.2 核心组件分层

```
@DataScope 注解（标记需要过滤的方法）
  ↓
DataScopeAspect 切面（调用 resolveDataScope，放入 ThreadLocal）
  ↓
DataScopeResolver（调用 auth-service 查询可见范围）
  ↓
DataScope 对象（承载可见范围数据）
  ↓
DataScopeContext（ThreadLocal 传递）
  ↓
Repository（拼接 QueryWrapper 条件）
```

### 5.3 @DataScope 注解定义

```java
// 位置：auth-api/annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    /**
     * 业务编码，用于查询该用户在此业务下的可见范围。
     * 与 @RequirePermission 的 business 保持一致。
     */
    String business();
    
    /**
     * 过滤维度，决定 Repository 拼接哪个字段。
     * PLAN：按 plan_no 过滤
     * CUSTOMER：按 customer_no 过滤
     * 默认 PLAN：大多数业务表都包含 plan_no
     */
    DataScopeDimension dimension() default DataScopeDimension.PLAN;
}
```

```java
public enum DataScopeDimension {
    PLAN,      // 按 plan_no 过滤
    CUSTOMER   // 按 customer_no 过滤
}
```

### 5.4 DataScope 对象

```java
// 位置：auth-api/dto
public record DataScope(
    boolean globalVisible,         // 是否全局可见（GLOBAL 范围）
    Set<String> visiblePlans,      // 可见 plan 列表
    Set<String> visibleCustomers,  // 可见 customer 列表（含继承的子客户）
    Set<String> excludedPlans,     // DENY 排除的 plan
    Set<String> excludedCustomers  // DENY 排除的 customer
) {
    public boolean needsFiltering() {
        return !globalVisible;
    }
    
    public static DataScope empty() {
        return new DataScope(false, Set.of(), Set.of(), Set.of(), Set.of());
    }
    
    public static DataScope global() {
        return new DataScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
```

### 5.5 DataScopeResolver 接口与默认实现

```java
// 位置：shared-permission-starter
public interface DataScopeResolver {
    /**
     * 解析当前用户的可见数据范围。
     * @param business 业务编码
     * @return 可见范围，失败返回 empty()（fail-closed）
     */
    DataScope resolve(String business);
}
```

```java
@Component
@RequiredArgsConstructor
public class DefaultDataScopeResolver implements DataScopeResolver {
    private final PermissionCheckApi permissionCheckApi;
    private final AccountIdResolver accountIdResolver;
    
    @Override
    public DataScope resolve(String business) {
        String accountId = accountIdResolver.resolveCurrentRequest();
        if (accountId == null || accountId.isBlank()) {
            return DataScope.empty();  // fail-closed
        }
        
        try {
            ApiResult<DataScopeResponse> result = permissionCheckApi.resolveDataScope(
                new DataScopeRequest(accountId, business));
            if (result == null || !result.isSuccess() || result.data() == null) {
                return DataScope.empty();
            }
            return toDataScope(result.data());
        } catch (Exception e) {
            log.warn("[DataScopeResolver] 调用 auth-service 失败, fail-closed. account={}, business={}",
                accountId, business, e);
            return DataScope.empty();
        }
    }
    
    private DataScope toDataScope(DataScopeResponse resp) {
        Set<String> visiblePlans = new HashSet<>(resp.visiblePlans());
        Set<String> visibleCustomers = new HashSet<>(resp.visibleCustomers());
        visiblePlans.removeAll(resp.excludedPlans());
        visibleCustomers.removeAll(resp.excludedCustomers());
        return new DataScope(
            resp.globalVisible(),
            visiblePlans,
            visibleCustomers,
            resp.excludedPlans(),
            resp.excludedCustomers()
        );
    }
}
```

### 5.6 auth-service 端 resolveDataScope 实现

#### PermissionCheckApi 扩展

```java
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {
    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);
    
    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@RequestBody @Valid PermissionCheckBatchRequest request);
    
    // 新增：解析数据可见范围
    @PostExchange("/resolve-data-scope")
    ApiResult<DataScopeResponse> resolveDataScope(@RequestBody @Valid DataScopeRequest request);
}
```

#### 请求/响应 DTO

```java
// 位置：auth-api/command
public record DataScopeRequest(
    @NotBlank String accountId,
    @NotBlank String businessCode) {}

// 位置：auth-api/dto
public record DataScopeResponse(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers) {}
```

#### PermissionQueryService 扩展

```java
// 位置：auth-application
public DataScope resolveDataScope(ResolveDataScopeQuery query) {
    UserNo identity = query.identity();
    BusinessCode business = query.business();
    LocalDateTime at = LocalDateTime.now();
    
    // 1. 查询所有授权该用户的持久化 Grant
    List<Grant> persistedGrants = grantRepository.findCandidateSubjectGrants(identity, at).stream()
        .filter(g -> g.isActiveAt(at))
        .filter(g -> g.coversBusiness(business))
        .toList();
    
    // 2. 查询该用户的所有 Assignment，解析为虚拟 Grant
    List<Grant> liveGrants = effectivePermissionService.resolveLiveRoleTemplateGrants(identity, at).stream()
        .filter(g -> g.coversBusiness(business))
        .toList();
    
    // 3. 聚合可见范围
    Set<String> visiblePlans = new HashSet<>();
    Set<String> visibleCustomers = new HashSet<>();
    Set<String> deniedPlans = new HashSet<>();
    Set<String> deniedCustomers = new HashSet<>();
    boolean isGlobal = false;
    
    for (Grant g : concat(persistedGrants, liveGrants)) {
        boolean isAllow = g.effect() == Effect.ALLOW;
        for (ScopeRule rule : g.scopeRules()) {
            switch (rule.dimension()) {
                case GLOBAL -> { if (isAllow) isGlobal = true; }
                case PLAN -> {
                    if (isAllow) visiblePlans.add(rule.value());
                    else deniedPlans.add(rule.value());
                }
                case CUSTOMER -> {
                    if (isAllow) {
                        visibleCustomers.add(rule.value());
                        if (rule.inheritable()) {
                            visibleCustomers.addAll(
                                orgDirectory.descendantsOf(CustomerNo.of(rule.value()))
                                    .stream().map(CustomerNo::value).toList());
                        }
                    } else {
                        deniedCustomers.add(rule.value());
                    }
                }
                // 其他维度暂不参与行级过滤
            }
        }
    }
    
    // 4. 排除 DENY
    visiblePlans.removeAll(deniedPlans);
    visibleCustomers.removeAll(deniedCustomers);
    
    // 5. 全局可见时不返回列表（减少数据传输）
    if (isGlobal) {
        return DataScope.global();
    }
    
    return new DataScope(false, visiblePlans, visibleCustomers, deniedPlans, deniedCustomers);
}
```

#### 短路调用（auth-service 内部）

与功能权限校验的 `LocalPermissionExecutor` 机制一样，DataScopeResolver 在 auth-service 中通过 `LocalDataScopeResolver` 短路调用 `PermissionQueryService.resolveDataScope`，避免 HttpExchange 循环：

```java
// 位置：auth-adapter
@Component
@Primary  // 覆盖默认的 DefaultDataScopeResolver
public class LocalDataScopeResolver implements DataScopeResolver {
    private final PermissionQueryService permissionQueryService;
    private final AccountIdResolver accountIdResolver;
    
    @Override
    public DataScope resolve(String business) {
        String accountId = accountIdResolver.resolveCurrentRequest();
        if (accountId == null || accountId.isBlank()) {
            return DataScope.empty();  // fail-closed
        }
        try {
            ResolveDataScopeQuery query = new ResolveDataScopeQuery(
                UserNo.of(accountId), new BusinessCode(business));
            return permissionQueryService.resolveDataScope(query);
        } catch (Exception e) {
            log.warn("[LocalDataScopeResolver] 解析失败, fail-closed. account={}, business={}",
                accountId, business, e);
            return DataScope.empty();
        }
    }
}
```

`DefaultDataScopeResolver` 标注 `@ConditionalOnMissingBean(DataScopeResolver.class)`，在 auth-service 中被 `LocalDataScopeResolver`（`@Primary`）覆盖。业务服务仍使用 `DefaultDataScopeResolver` 走 HttpExchange。

### 5.7 DataScopeAspect 切面

```java
// 位置：shared-permission-starter
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {
    private final DataScopeResolver dataScopeResolver;
    
    @Around("@annotation(dataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        try {
            DataScope scope = dataScopeResolver.resolve(dataScope.business());
            DataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();  // 清理 ThreadLocal，避免内存泄漏
        }
    }
}
```

### 5.8 DataScopeContext（ThreadLocal 传递）

```java
// 位置：shared-permission-starter
public final class DataScopeContext {
    private static final ThreadLocal<DataScope> HOLDER = new ThreadLocal<>();
    
    public static void set(DataScope scope) { HOLDER.set(scope); }
    
    public static DataScope get() {
        DataScope scope = HOLDER.get();
        return scope != null ? scope : DataScope.empty();
    }
    
    public static void clear() { HOLDER.remove(); }
    
    /**
     * 获取当前 DataScope，未设置时抛异常（强约束场景用）。
     */
    public static DataScope require() {
        DataScope scope = HOLDER.get();
        if (scope == null) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("DataScopeContext 未设置，可能未标注 @DataScope 注解");
        }
        return scope;
    }
}
```

### 5.9 Repository 层使用

#### 基础工具方法

```java
// 位置：shared-permission-starter
public final class DataScopeQueryHelper {
    
    /**
     * 应用 plan_no 维度的过滤条件。
     * @param wrapper MyBatis-Flex QueryWrapper
     * @param planNoColumn plan_no 列定义（如 BATCH_DO.PLAN_NO）
     */
    public static void applyPlanScope(QueryWrapper wrapper, QueryColumn planNoColumn) {
        DataScope scope = DataScopeContext.get();
        if (!scope.needsFiltering()) {
            return;  // 全局可见，不拼接条件
        }
        if (scope.visiblePlans().isEmpty()) {
            wrapper.and("1=0");  // 空集：查不到任何数据
            return;
        }
        wrapper.and(planNoColumn.in(scope.visiblePlans()));
    }
    
    /**
     * 应用 customer_no 维度的过滤条件。
     */
    public static void applyCustomerScope(QueryWrapper wrapper, QueryColumn customerNoColumn) {
        DataScope scope = DataScopeContext.get();
        if (!scope.needsFiltering()) {
            return;
        }
        if (scope.visibleCustomers().isEmpty()) {
            wrapper.and("1=0");
            return;
        }
        wrapper.and(customerNoColumn.in(scope.visibleCustomers()));
    }
}
```

#### 业务 Repository 使用示例

```java
// annuity-infrastructure
@Override
public PageData<BatchDO> findBatches(ListBatchQuery query) {
    QueryWrapper wrapper = QueryWrapper.create()
        .where(BATCH_DO.DELETED.eq(false));
    
    // 行级数据过滤：按 plan_no 过滤
    DataScopeQueryHelper.applyPlanScope(wrapper, BATCH_DO.PLAN_NO);
    
    // 业务查询条件
    if (query.batchNo() != null) {
        wrapper.and(BATCH_DO.BATCH_NO.eq(query.batchNo()));
    }
    if (query.status() != null) {
        wrapper.and(BATCH_DO.STATUS.eq(query.status()));
    }
    return mapper.paginate(wrapper);
}
```

#### ApplicationService 使用示例

```java
// annuity-application
@Service
@AllArgsConstructor
public class AnnuityApplicationService {
    private final AnnuityRepository annuityRepository;
    
    @DataScope(business = "ANNUITY")  // 标记需要行级过滤
    @Transactional(readOnly = true)
    public PageData<BatchStatusDTO> listBatches(ListBatchQuery query) {
        return annuityRepository.findBatches(query);
    }
    
    @DataScope(business = "ANNUITY", dimension = DataScopeDimension.CUSTOMER)
    @Transactional(readOnly = true)
    public List<ApplicationDTO> listApplicationsByCustomer(ApplicationQuery query) {
        return annuityRepository.findApplications(query);
    }
}
```

### 5.10 @DataScope 与 @RequirePermission 的关系

两个注解独立工作，各司其职：

| 维度 | @RequirePermission | @DataScope |
|------|-------------------|------------|
| 作用 | 功能权限校验（垂直越权防护） | 行级数据过滤（水平越权防护） |
| 触发时机 | 方法执行前 | 方法执行前（设置 ThreadLocal） |
| 实现机制 | AOP 切面 → PermissionExecutor.check() | AOP 切面 → resolveDataScope() |
| 失败行为 | 抛 BusinessException | 返回空 DataScope → 查询不到数据 |
| 适用范围 | 所有业务类和管理类 API | 仅业务类 API（列表查询） |

典型组合用法：

```java
@DataScope(business = "ANNUITY")
@RequirePermission(business = "ANNUITY", action = "VIEW")
@Transactional(readOnly = true)
public PageData<BatchStatusDTO> listBatches(ListBatchQuery query) {
    return annuityRepository.findBatches(query);
}
```

切面执行顺序（`@RequirePermission` 先校验功能权限，`@DataScope` 再设置可见范围）：

```java
@Order(1)  // RequirePermissionAspect 先执行
public class RequirePermissionAspect { ... }

@Order(2)  // DataScopeAspect 后执行
public class DataScopeAspect { ... }
```

### 5.11 详情查询的水平越权防护

详情查询（如 `getApplication(applicationId)`）通过 Repository 的 `findById` 方法应用 DataScope 条件：

```java
@Override
public ApplicationDO findById(Long id) {
    QueryWrapper wrapper = QueryWrapper.create()
        .where(APPLICATION_DO.ID.eq(id))
        .and(APPLICATION_DO.DELETED.eq(false));
    DataScopeQueryHelper.applyPlanScope(wrapper, APPLICATION_DO.PLAN_NO);
    return mapper.selectOneByQuery(wrapper);
}
```

效果：用户访问不在自己可见范围内的 application 时，`findById` 返回 null，业务层抛"不存在"异常，达到水平越权防护效果（不抛"无权限"异常，避免泄露资源存在性）。

### 5.12 边界场景

| 场景 | 处理方式 |
|------|---------|
| X-Account-Id 缺失 | DataScope 返回 empty()，查询不到数据（fail-closed） |
| auth-service 不可达 | DataScope 返回 empty()，查询不到数据（fail-closed） |
| 用户无任何授权 | DataScope 返回 empty()（visiblePlans 为空），查询不到数据 |
| 用户有 GLOBAL 授权 | DataScope 返回 global()，不拼接条件，返回所有数据 |
| 用户有 DENY 授权 | excludedPlans 从 visiblePlans 中排除 |
| 跨表查询（JOIN） | DataScopeContext 在同一请求内有效，多个 Repository 调用共享同一 DataScope |
| 异步场景（@Async） | ThreadLocal 不跨线程传递，需要手动传递 DataScope 或标记不支持异步 |

### 5.13 性能考量

#### resolveDataScope 调用频率

每次 `@DataScope` 方法调用都会触发一次 resolveDataScope。初版不实现缓存，保持简单。后续按性能测试结果决定是否引入 Caffeine 缓存（可选优化）：

```java
@Component
public class CachedDataScopeResolver implements DataScopeResolver {
    private final Cache<String, DataScope> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build();
    
    @Override
    public DataScope resolve(String business) {
        String accountId = accountIdResolver.resolveCurrentRequest();
        String cacheKey = accountId + ":" + business;
        return cache.get(cacheKey, k -> delegate.resolve(business));
    }
}
```

#### 大列表 IN 查询

当用户可见 plan 数量较多（如 1000 个），`plan_no IN (...)` 查询性能会下降。应对策略：
- 短期：接受性能损耗，业务规模有限
- 长期：考虑改用 EXISTS 子查询或临时表关联

## 六、kernel 权限点自动上报机制

### 6.1 问题背景

当前 `PermissionScanner` 只在 auth-service 启动时扫描本地 `RequestMappingHandlerMapping`。但 kernel 的 5 类 Controller 在业务服务实例中才被 Spring MVC 注册。后果：kernel 标注的 `@RequirePermission` 不会被注册到 `t_auth_permission_item` 表，权限判定时 `resolveCategory()` 查不到记录 → 默认走 BUSINESS。

### 6.2 设计目标

业务服务启动时，自动扫描自身的 `@RequirePermission` 注解，上报到 auth-service 的 `t_auth_permission_item` 表。核心原则：
- 复用现有 PermissionScanner 的扫描逻辑
- 通过 HttpExchange 上报到 auth-service 新增的注册接口
- 幂等上报（多次启动不会产生重复记录）
- 上报失败不阻断业务服务启动（fail-soft）

### 6.3 架构设计

```
业务服务启动
  ↓
PermissionRegistrationRunner（shared-permission-starter）
  ├─ 扫描本地 RequestMappingHandlerMapping
  ├─ 提取 @RequirePermission 注解元数据
  └─ 调用 PermissionRegistrationApi 上报到 auth-service
      ↓
auth-service PermissionRegistrationController
  ├─ 接收上报数据
  └─ 调用 PermissionScannerService.registerFromExternal（复用 upsert 逻辑）
      ↓
t_auth_permission_item 表（统一存储所有权限点）
```

### 6.4 PermissionRegistrationApi（新增）

```java
// 位置：auth-api
@HttpExchange("/internal/permission-registration")
public interface PermissionRegistrationApi {
    
    /**
     * 批量上报权限点。
     * @param request 包含来源服务名 + 权限点列表
     */
    @PostExchange("/register")
    ApiResult<PermissionRegistrationResponse> register(
        @RequestBody @Valid PermissionRegistrationRequest request);
}
```

### 6.5 上报请求/响应 DTO

```java
// 位置：auth-api/command
public record PermissionRegistrationRequest(
    @NotBlank String sourceService,           // 来源服务名（如 annuity-service）
    @NotEmpty @Valid List<PermissionItemDescriptor> items) {}

public record PermissionItemDescriptor(
    @NotBlank String businessCode,
    String actionCode,
    @NotBlank String category,                // BUSINESS / PLATFORM
    String controller,
    String method,
    String httpMethod,
    String path) {}

// 位置：auth-api/dto
public record PermissionRegistrationResponse(
    int totalReceived,     // 接收的权限点数量
    int upserted,          // 新增或更新的数量
    int unchanged) {}       // 未变化的数量
```

### 6.6 PermissionRegistrationController

```java
// 位置：auth-adapter
@RestController
@AllArgsConstructor
public class PermissionRegistrationController implements PermissionRegistrationApi {
    
    private final PermissionScannerService scannerService;
    
    @Override
    public ApiResult<PermissionRegistrationResponse> register(
        PermissionRegistrationRequest request) {
        PermissionRegistrationResult result = scannerService.registerFromExternal(
            request.sourceService(),
            request.items());
        return ApiResult.success(new PermissionRegistrationResponse(
            result.totalReceived(),
            result.upserted(),
            result.unchanged()));
    }
}
```

注意：此 Controller 不标注 `@RequirePermission`，因为它是内部接口（路径 `/internal/permission-registration`），被网关 `ExcludeRouteFilter` 403 拦截，仅服务间调用可达。

### 6.7 PermissionScannerService 重构

将现有 PermissionScanner 的核心逻辑抽取为 PermissionScannerService：

```java
// 位置：auth-application
@Service
@AllArgsConstructor
public class PermissionScannerService {
    private final PermissionItemRepository repository;
    
    /**
     * 扫描本地 Controller（auth-service 自身的权限点）。
     * 由 auth-service 启动时调用。
     */
    public ScanResult scanLocal(RequestMappingHandlerMapping handlerMapping, UserNo scanner) {
        List<PermissionItemDescriptor> descriptors = extractDescriptors(handlerMapping);
        List<PermissionItem> items = descriptors.stream().map(d -> toItem(d, scanner)).toList();
        repository.upsertAll(items, scanner);
        
        // 只标记 auth-service 自身的未扫描权限点
        Set<PermissionItemId> scannedIds = items.stream()
            .map(item -> repository.findByBusinessAndAction(item.businessCode(), item.actionCode())
                .map(PermissionItem::id).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        repository.markStaleForUnscanned(scannedIds, scanner);
        
        return new ScanResult(items.size(), items.size(), 0);
    }
    
    /**
     * 注册外部业务服务上报的权限点。
     */
    public PermissionRegistrationResult registerFromExternal(
        String sourceService, List<PermissionItemDescriptor> items) {
        // 不执行 markStaleForUnscanned
        UserNo scanner = UserNo.of("scanner:" + sourceService);
        List<PermissionItem> permissionItems = items.stream()
            .map(d -> toItem(d, scanner)).toList();
        int upserted = repository.upsertAll(permissionItems, scanner);
        return new PermissionRegistrationResult(items.size(), upserted, items.size() - upserted);
    }
}
```

### 6.8 PermissionRegistrationRunner

```java
// 位置：shared-permission-starter
@Component
@ConditionalOnBean(PermissionRegistrationApi.class)
@ConditionalOnExpression("'${spring.application-name}' != 'auth-service'")
@RequiredArgsConstructor
public class PermissionRegistrationRunner implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionRegistrationRunner.class);
    
    private final RequestMappingHandlerMapping handlerMapping;
    private final PermissionRegistrationApi registrationApi;
    private final Environment environment;
    
    @Override
    public void run(ApplicationArguments args) {
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        
        try {
            List<PermissionItemDescriptor> descriptors = extractDescriptors();
            if (descriptors.isEmpty()) {
                log.info("[PermissionRegistration] 服务 {} 未发现 @RequirePermission 注解，跳过上报", serviceName);
                return;
            }
            
            log.info("[PermissionRegistration] 服务 {} 开始上报 {} 个权限点", serviceName, descriptors.size());
            ApiResult<PermissionRegistrationResponse> result = registrationApi.register(
                new PermissionRegistrationRequest(serviceName, descriptors));
            
            if (result != null && result.isSuccess()) {
                PermissionRegistrationResponse data = result.data();
                log.info("[PermissionRegistration] 服务 {} 上报完成: 接收 {}, 新增/更新 {}, 未变化 {}",
                    serviceName, data.totalReceived(), data.upserted(), data.unchanged());
            } else {
                log.warn("[PermissionRegistration] 服务 {} 上报失败: {} - {}", 
                    serviceName, 
                    result != null ? result.getCode() : "null",
                    result != null ? result.getMessage() : "响应为空");
            }
        } catch (Exception e) {
            // fail-soft：上报失败不阻断业务服务启动
            log.warn("[PermissionRegistration] 服务 {} 上报权限点失败,不影响启动", serviceName, e);
        }
    }
    
    private List<PermissionItemDescriptor> extractDescriptors() {
        List<PermissionItemDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequirePermission annotation = AnnotationUtils.findAnnotation(
                entry.getValue().getMethod(), RequirePermission.class);
            if (annotation == null) continue;
            
            String path = extractPath(entry.getKey());
            String httpMethod = extractHttpMethod(entry.getKey());
            String action = annotation.action().isEmpty() ? null : annotation.action();
            
            descriptors.add(new PermissionItemDescriptor(
                annotation.business(),
                action,
                annotation.category().name(),
                entry.getValue().getBeanType().getSimpleName(),
                entry.getValue().getMethod().getName(),
                httpMethod,
                path));
        }
        return descriptors;
    }
}
```

关键设计点：
- `@ConditionalOnBean(PermissionRegistrationApi.class)`：只有配置了 httpexchange 客户端的业务服务才启用
- `@ConditionalOnExpression` 排除 auth-service 自身（它自己有 PermissionScanner 扫描本地）
- `fail-soft`：上报失败只记录 WARN 日志，不阻断启动
- 提取逻辑与 PermissionScanner 一致，但不上报 auth-service 自身的权限点

### 6.9 auth-service 自身扫描的调整

现有 PermissionScanner 改为委托 PermissionScannerService：

```java
// 位置：auth-infrastructure
@Component
@RequiredArgsConstructor
public class PermissionScanner implements ApplicationRunner {
    
    private final PermissionScannerService scannerService;
    private final RequestMappingHandlerMapping handlerMapping;
    
    @Override
    public void run(ApplicationArguments args) {
        UserNo scanner = UserNo.of("permission-scanner");
        ScanResult result = scannerService.scanLocal(handlerMapping, scanner);
        log.info("[PermissionScanner] auth-service 本地扫描完成: 发现 {}, 新增/更新 {}, 未变化 {}",
            result.totalReceived(), result.upserted(), result.unchanged());
    }
}
```

### 6.10 幂等性保证

现有 `PermissionItemRepositoryImpl.upsertAll` 按 `(businessCode, actionCode)` 唯一键 upsert：
- 不存在 → INSERT
- 已存在 → UPDATE 来源字段（controller/method/httpMethod/path），保留人工补充的元数据（displayName/categoryGroup/sortOrder）

效果：业务服务多次重启上报，不会产生重复记录，也不会覆盖人工补充的元数据。

### 6.11 markStaleForUnscanned 的调整

- `scanLocal`（auth-service 启动）执行 `markStaleForUnscanned`，但只标记 auth-service 自身的权限点
- `registerFromExternal`（业务服务上报）不执行 `markStaleForUnscanned`，只 upsert

避免业务服务之间互相影响。

### 6.12 业务服务配置

每个业务服务在 starter 的 application-local.yml 中添加 PermissionRegistrationApi 客户端配置：

```yaml
io:
  github:
    danielliu1123:
      httpexchange:
        clients:
          - client-class-name: com.example.auth.api.PermissionCheckApi
            url: lb://auth-service
          # 新增
          - client-class-name: com.example.auth.api.PermissionRegistrationApi
            url: lb://auth-service
```

### 6.13 启动顺序

```
1. auth-service 启动 → PermissionScanner 扫描本地 Controller → 注册自身权限点
2. 业务服务启动 → PermissionRegistrationRunner 上报权限点到 auth-service
```

关键：auth-service 必须先启动。如果业务服务先启动，上报会失败（auth-service 不可达），但 fail-soft 不阻断启动，业务服务下次重启时会重新上报。

### 6.14 来源服务追踪（可选增强）

为在管理后台区分权限点来源，可在 `t_auth_permission_item` 表新增 `source_service` 字段：

```sql
ALTER TABLE t_auth_permission_item ADD COLUMN source_service VARCHAR(64);
COMMENT ON COLUMN t_auth_permission_item.source_service IS '来源服务名（如 annuity-service、auth-service）';
```

初版不实现，保持表结构不变。后续管理后台需要按服务筛选权限点时再添加。

## 七、垂直越权防护与管理类 API 标注规范

### 7.1 垂直越权防护机制

垂直越权指低权限用户访问高权限功能。当前体系通过 `@RequirePermission` + `PermissionExecutor.check()` 实现垂直越权防护：

```
用户请求 → @RequirePermission 切面 → PermissionExecutor.check()
  ↓
auth-service PermissionQueryService
  ├─ resolveCategory(business, action) → 查 t_auth_permission_item
  ├─ BUSINESS 类：checkPermission(identity, planId, permission, at)
  │   ├─ 能力层：checkPlanCapability(planId, business, at)
  │   └─ 主体层：checkSubjectGrant(identity, planId, permission, at)
  └─ PLATFORM 类：checkPlatformPermission(identity, permission, at)
      └─ 仅 GLOBAL 范围 Grant 匹配
  ↓
EffectResolver：DENY 优先 + 白名单语义（默认拒绝）
```

垂直越权防护的核心保证：
1. 未授权用户 → checkPermission 返回 false → 抛 PERMISSION_DENIED
2. 仅授权特定 action 的用户 → 其他 action 返回 false
3. DENY 优先 → 即使有 ALLOW 也有 DENY，最终拒绝
4. 默认拒绝 → 都没命中授权，返回 false

### 7.2 标注原则

| API 分类 | category | business 命名规范 | 示例 |
|---------|---------|-----------------|------|
| 渠道业务类 | BUSINESS（默认） | 业务领域大写 | `ANNUITY`、`APPROVAL_INSTANCE`、`FILE_TASK` |
| 系统管理类 | PLATFORM | 领域大写 + 下划线 | `USER`、`ROLE`、`GRANT`、`CHANNEL_ENTITLEMENT` |

关键：管理类 API 必须显式声明 `category = PermissionCategory.PLATFORM`，否则默认走 BUSINESS 路径需要 planId，但管理类 API 没有 planId 上下文。

### 7.3 未来系统管理后台 API 标注规范

| 规划 Controller | 路径前缀 | business | 示例 action |
|----------------|---------|----------|-----------|
| UserController | /admin/users | USER | VIEW、CREATE、UPDATE、DISABLE、RESET_PASSWORD |
| RoleController | /admin/roles | ROLE | VIEW、CREATE、UPDATE、DELETE、ASSIGN_PERMISSIONS |
| GrantController | /admin/grants | GRANT | VIEW、CREATE、APPROVE、REJECT、REVOKE |
| AssignmentController | /admin/assignments | ASSIGNMENT | VIEW、CREATE、DEACTIVATE |
| CredentialController | /admin/credentials | CREDENTIAL | VIEW、CREATE、RESET、DISABLE |
| ProductController | /admin/products | PRODUCT | VIEW、CREATE、UPDATE |

统一规范：
- 路径前缀统一 `/admin/**`
- 全部标注 `category = PermissionCategory.PLATFORM`
- action 命名使用大写下划线（CREATE/UPDATE/VIEW/DELETE 等）
- 列表查询和详情查询统一用 VIEW action

### 7.4 业务服务现有 PLATFORM 标注清单

| Controller | business | 已标注 action |
|-----------|----------|-------------|
| ApprovalFlowAdapter | APPROVAL_FLOW | CREATE、UPDATE、DEPRECATE、VIEW、MATCH |
| ApprovalInstanceAdapter | APPROVAL_INSTANCE | CREATE、APPROVE、REJECT、TRANSFER、VIEW 等 |
| FileTaskAdapter | FILE_TASK | CREATE、VIEW、CANCEL、RETRY |
| FileAccessAdapter | FILE_ACCESS | UPLOAD、DOWNLOAD、VIEW、DELETE |
| TradeQueryAdapter | TRADE_QUERY | QUERY_BALANCE |

需要补标注：
- `BusinessFormController.status` → 补标注 `@RequirePermission(business = "FORM", action = "VIEW")`

### 7.5 垂直越权防护完整性检查

#### 当前体系已具备的能力

| 防护点 | 实现机制 | 状态 |
|--------|---------|------|
| 未登录访问 | 网关 SaReactorFilter 登录校验 | 已实现 |
| 无权限访问功能 | @RequirePermission + PermissionCheckApi | 已实现 |
| 跨业务访问（A 业务用户访问 B 业务） | businessCode 不匹配 → checkPermission 返回 false | 已实现 |
| 跨 action 访问（有 VIEW 但无 CREATE） | actionCode 不匹配 → checkPermission 返回 false | 已实现 |
| DENY 覆盖 ALLOW | EffectResolver DENY 优先 | 已实现 |
| 无任何授权 | 默认拒绝（白名单语义） | 已实现 |

#### 本方案补齐的防护点

| 防护点 | 实现机制 | 新增 |
|--------|---------|-----|
| auth-service 自身 API 无防护 | 短路调用 + 标注 @RequirePermission | 第三节 |
| kernel 权限点未注册 | 业务服务启动上报 | 第六节 |
| 非渠道前缀路径网关放行 | 所有路径都校验登录 + yml 白名单 | 第三节 |
| 列表查询水平越权 | @DataScope + Repository 条件拼接 | 第五节 |

### 7.6 API 标注合规性强制检查

#### PermissionScanner 增加强制模式开关

```yaml
# auth-service 的 application.yml
auth:
  permission:
    scanner:
      # 是否强制所有 Controller 方法标注 @RequirePermission（true=未标注抛异常，false=仅告警）
      strict-mode: false  # 初版宽松，后续切换为 true
```

```java
// PermissionScannerService
if (annotation == null) {
    if (strictMode) {
        throw new IllegalStateException(String.format(
            "Controller 方法未标注 @RequirePermission: %s.%s",
            handlerMethod.getBeanType().getSimpleName(),
            handlerMethod.getMethod().getName()));
    } else {
        log.warn("未声明 @RequirePermission 的接口: {}.{}",
            handlerMethod.getBeanType().getSimpleName(),
            handlerMethod.getMethod().getName());
    }
    continue;
}
```

初版保持宽松模式（false），待所有 Controller 标注完成后切换为强制模式。

#### 豁免标注的接口清单

以下接口不需要标注 `@RequirePermission`：

| 接口 | 豁免理由 |
|------|---------|
| PermissionCheckController.check / checkBatch | 被 PermissionExecutor 调用的内部接口 |
| PermissionRegistrationController.register | 业务服务上报权限点的内部接口 |
| 登录接口（/internet/auth/login 等） | 登录前无需校验，在 yml public-paths 白名单 |
| 健康检查（/actuator/**） | 监控端点，在 yml public-paths 白名单 |
| 二次授权发起/确认接口 | 经办人未完成登录的特殊场景 |

实现方式：这些接口要么路径在 `/internal/**`（被网关 403 拦截），要么在 yml public-paths 白名单。PermissionScanner 扫描时仍会告警（未标注），但不会误拦截。

### 7.7 测试接口清理

`AnnuityLinkTestController` 应该：
- 移到 `src/test` 下：不应进入生产环境
- 或标注 `@Profile("dev")`：仅在开发环境启用
- 或删除：如果链路测试已完成

建议：移到测试代码或标注 `@Profile("dev")`，不进入生产构建。

### 7.8 管理类 API 的登录渠道要求

管理类 API 通过 `/admin/**` 路径访问，网关层用默认 StpLogic 校验登录态。用户可以通过任一渠道（internet/hq/branch）登录后访问管理类 API，但功能权限校验（PLATFORM 类）会检查用户是否有 GLOBAL 范围的 Grant。

潜在风险：internet 渠道用户（经办人）如果被错误配置了 GLOBAL 范围 Grant，可以访问管理后台。

防护：
- 业务规则约束：GLOBAL 范围 Grant 仅授予 HQ 渠道用户（通过 Assignment.scopeDimension=GLOBAL）
- 管理后台前端路由：仅 HQ 渠道登录后展示管理入口
- 后端不引入"渠道-路径"硬绑定（遵循决策"仅功能权限判定"）

### 7.9 错误码补充

现有 `PermissionErrorCode` 已有 4 个错误码，本次新增 1 个：

```java
public enum PermissionErrorCode implements ErrorDefinition {
    PERMISSION_DENIED("SHARED.PERM.0001", "无权限访问"),
    PERMISSION_SERVICE_UNAVAILABLE("SHARED.PERM.0002", "权限校验服务暂不可用"),
    SESSION_SIGNATURE_INVALID("SHARED.PERM.0003", "会话签名验证失败"),
    SESSION_CONTEXT_MISSING("SHARED.PERM.0004", "会话上下文缺失"),
    DATA_SCOPE_RESOLVE_FAILED("SHARED.PERM.0005", "数据范围解析失败");  // 新增
}
```

使用场景：当 DataScopeResolver 解析失败且需要明确告知是数据范围问题时使用。当前设计中 DataScopeResolver fail-closed 返回 empty()，不抛异常。此错误码预留给未来需要强约束的场景。

## 八、实施清单

### 8.1 改动总览

| 模块 | 改动类型 | 文件数 |
|------|---------|--------|
| demo-gateway | 改造 + 删除 | 9 |
| auth-api | 新增 + 改造 + 删除 | 12 |
| auth-adapter | 新增 + 改造 + 删除 | 9 |
| auth-application | 新增 + 改造 | 3 |
| auth-infrastructure | 改造 | 2 |
| shared-permission-starter | 新增 + 改造 | 11 |
| business-core-adapter | 改造 | 1 |
| 业务服务 starter（4 个） | 改造 | 4 |
| 错误码规范 | 更新 | 1 |

### 8.2 demo-gateway 改动清单

#### 改造文件

| 文件 | 改动内容 |
|------|---------|
| `SaTokenGatewayConfiguration.java` | setAuth 改造：channel==null 时走默认 StpUtil.checkLogin()；移除 addExclude 硬编码，改读 GatewayProperties |
| `ChannelAwareSaRouter.java` | 新增 matchChannel(path) 方法；新增 configureDefaultStpLogic() 配置默认 StpLogic 识别所有渠道 token |
| `SessionContextInjector.java` | 扩展支持非渠道前缀路径，调用 resolveAnyChannelLoginId() 注入 X-Account-Id |
| `GatewayProperties.java` | 新增配置类，消费 auth.gateway.public-paths |
| `application.yml` | public-paths 新增 /admin/auth/login；移除 httpexchange RouteRuleApi 配置 |
| `GatewayStpInterfaceImplTest.java` | 调整测试以匹配新的 setAuth 逻辑 |

#### 删除文件

| 文件 | 原因 |
|------|------|
| `RouteRuleLoader.java` | RouteRule 机制移除 |
| `RouteRule.java` | RouteRule 机制移除 |
| `RouteRuleTest.java` | RouteRule 机制移除 |

### 8.3 auth-api 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `PermissionCheckApi.java` | 改造 | 新增 resolveDataScope 方法 |
| `PermissionRegistrationApi.java` | 新增 | 上报权限点接口 |
| `annotation/DataScope.java` | 新增 | 行级过滤注解 |
| `annotation/DataScopeDimension.java` | 新增 | 过滤维度枚举（PLAN/CUSTOMER） |
| `dto/DataScope.java` | 新增 | 数据可见范围对象 |
| `command/DataScopeRequest.java` | 新增 | resolveDataScope 请求 DTO |
| `dto/DataScopeResponse.java` | 新增 | resolveDataScope 响应 DTO |
| `command/PermissionRegistrationRequest.java` | 新增 | 上报请求 DTO |
| `dto/PermissionItemDescriptor.java` | 新增 | 权限点描述符 |
| `dto/PermissionRegistrationResponse.java` | 新增 | 上报响应 DTO |
| `RouteRuleApi.java` | 删除 | RouteRule 移除 |
| `dto/RouteRuleResponse.java` | 删除 | RouteRule 移除 |

### 8.4 auth-adapter 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `PermissionCheckController.java` | 改造 | 新增 resolveDataScope 端点 |
| `PermissionRegistrationController.java` | 新增 | 接收业务服务上报 |
| `LocalPermissionExecutor.java` | 新增 | auth-service 功能权限短路调用实现 |
| `LocalDataScopeResolver.java` | 新增 | auth-service 数据范围短路调用实现 |
| `CustomerChannelEntitlementController.java` | 改造 | 标注 @RequirePermission(PLATFORM) |
| `PermissionMetadataController.java` | 改造 | 标注 @RequirePermission(PLATFORM) |
| `PermissionCacheController.java` | 改造 | 标注 @RequirePermission(PLATFORM) |
| `RouteRuleController.java` | 删除 | RouteRule 移除 |
| `pom.xml` | 改造 | 引入 shared-permission-starter |

### 8.5 auth-application 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `PermissionQueryService.java` | 改造 | 新增 resolveDataScope 方法 |
| `PermissionScannerService.java` | 新增 | 扫描逻辑抽取 + registerFromExternal 方法 |
| `CheckPermissionQuery.java` | 改造 | 新增 ResolveDataScopeQuery |

### 8.6 auth-infrastructure 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `PermissionScanner.java` | 改造 | 简化为委托 PermissionScannerService |
| `PermissionItemRepositoryImpl.java` | 改造 | upsertAll 返回新增/更新数量；markStaleForUnscanned 只处理 auth-service 自身权限点 |

### 8.7 shared-permission-starter 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `RequirePermissionAspect.java` | 改造 | 改为依赖 PermissionExecutor 接口，不再直接依赖 PermissionCheckApi |
| `PermissionExecutor.java` | 新增 | 权限校验执行接口 |
| `HttpExchangePermissionExecutor.java` | 新增 | 业务服务默认实现（HttpExchange） |
| `DataScopeResolver.java` | 新增 | 数据可见范围解析接口 |
| `DefaultDataScopeResolver.java` | 新增 | 默认实现（调用 PermissionCheckApi.resolveDataScope） |
| `DataScopeAspect.java` | 新增 | @DataScope 切面 |
| `DataScopeContext.java` | 新增 | ThreadLocal 传递 |
| `DataScopeQueryHelper.java` | 新增 | Repository 条件拼接工具 |
| `PermissionRegistrationRunner.java` | 新增 | 业务服务启动上报权限点 |
| `errorcode/PermissionErrorCode.java` | 改造 | 新增 DATA_SCOPE_RESOLVE_FAILED |
| `pom.xml` | 改造 | 引入 spring-web（已存在）、Caffeine（可选） |

### 8.8 business-core-adapter 改动清单

| 文件 | 改动类型 | 内容 |
|------|---------|------|
| `BusinessFormController.java` | 改造 | 补标注 status 方法的 @RequirePermission |

### 8.9 业务服务 starter 改动清单

| 文件 | 改动内容 |
|------|---------|
| `approval-starter/application-local.yml` | httpexchange clients 新增 PermissionRegistrationApi |
| `file-starter/application-local.yml` | httpexchange clients 新增 PermissionRegistrationApi |
| `integration-starter/application-local.yml` | httpexchange clients 新增 PermissionRegistrationApi |
| `annuity-starter/application-local.yml` | httpexchange clients 新增 PermissionRegistrationApi |

### 8.10 规范文档更新

| 文件 | 改动内容 |
|------|---------|
| `08-错误码规范.md` | SHARED.PERM 模块新增 0005 DATA_SCOPE_RESOLVE_FAILED |

## 九、实施顺序

按依赖关系分 5 个阶段：

### 阶段 1：基础组件（无依赖）
- auth-api：新增 DTO、注解、PermissionRegistrationApi、扩展 PermissionCheckApi
- shared-permission-starter：新增 PermissionExecutor、DataScopeResolver、DataScopeAspect、DataScopeContext、DataScopeQueryHelper、PermissionRegistrationRunner

### 阶段 2：auth-service 改造（依赖阶段 1）
- auth-application：PermissionScannerService、PermissionQueryService.resolveDataScope
- auth-infrastructure：PermissionScanner 简化、PermissionItemRepositoryImpl 调整
- auth-adapter：LocalPermissionExecutor、PermissionRegistrationController、标注 @RequirePermission、删除 RouteRuleController

### 阶段 3：网关改造（依赖阶段 1）
- demo-gateway：SaTokenGatewayConfiguration、ChannelAwareSaRouter、SessionContextInjector、GatewayProperties、删除 RouteRule 相关、application.yml 调整

### 阶段 4：业务服务接入（依赖阶段 1、2）
- business-core-adapter：BusinessFormController 补标注
- 4 个业务服务 starter：application-local.yml 配置 PermissionRegistrationApi

### 阶段 5：清理与验证
- 删除 RouteRule 相关文件
- AnnuityLinkTestController 移到测试代码或加 @Profile("dev")
- 更新 08-错误码规范.md
- 全量编译验证
- 单元测试
- 集成测试

## 十、测试策略

### 10.1 单元测试

| 测试类 | 验证点 |
|--------|--------|
| `GatewayPropertiesTest` | yml 配置被正确消费，isPublicPath 行为正确 |
| `SaTokenGatewayConfigurationTest` | 渠道前缀走渠道 StpLogic，非渠道前缀走默认 StpLogic |
| `SessionContextInjectorTest` | 非渠道前缀路径也能注入 X-Account-Id |
| `LocalPermissionExecutorTest` | 本地调用 PermissionQueryService，不发起 HttpExchange |
| `LocalDataScopeResolverTest` | 本地调用 PermissionQueryService.resolveDataScope，不发起 HttpExchange |
| `RequirePermissionAspectTest` | auth-service 走本地短路，业务服务走 HttpExchange |
| `DataScopeAspectTest` | 切面正确设置和清理 ThreadLocal |
| `DataScopeContextTest` | ThreadLocal 的 get/set/clear 行为 |
| `DataScopeQueryHelperTest` | QueryWrapper 条件拼接正确性 |
| `PermissionScannerServiceTest` | scanLocal 和 registerFromExternal 的 upsert 行为 |
| `PermissionRegistrationRunnerTest` | 扫描本地 Controller 并上报，fail-soft 行为 |
| `PermissionQueryService.resolveDataScopeTest` | 聚合逻辑（GLOBAL/PLAN/CUSTOMER/DENY） |

### 10.2 集成测试

| 场景 | 验证点 |
|------|--------|
| 非渠道前缀路径登录校验 | `/admin/users/list` 未登录 → 401；已登录 → 通过 |
| auth-service 自身 API 权限校验 | 无权限用户访问 CustomerChannelEntitlementController → 403 |
| 业务服务权限点上报 | annuity-service 启动 → t_auth_permission_item 表有 ANNUITY 权限点 |
| 行级数据过滤 | 用户 A 有 P001 可见范围，查询列表仅返回 P001 数据 |
| 水平越权防护 | 用户 A 访问 P002 详情 → 返回 null（不抛异常） |
| 垂直越权防护 | 用户 A 无 USER:VIEW → 访问 `/admin/users/list` → 403 |
| DENY 优先 | 用户 A 同时有 ALLOW 和 DENY → 拒绝 |

## 十一、风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| auth-service 启动顺序依赖 | 业务服务 PermissionRegistrationRunner fail-soft，不阻断启动 |
| ThreadLocal 内存泄漏 | DataScopeAspect finally 块强制 clear |
| resolveDataScope 性能 | 初版不缓存，后续按性能测试结果引入 Caffeine 缓存 |
| 大列表 IN 查询性能 | 初版接受损耗，后续改用 EXISTS 子查询 |
| 默认 StpLogic 配置错误导致所有 token 失效 | 单元测试覆盖默认 StpLogic 识别所有渠道 token |
| PermissionScanner 强制模式误拦截 | 初版保持宽松模式（strict-mode=false） |

## 十二、不在本次范围内

以下内容明确不在本次实施范围：

1. 系统管理后台 Controller 实现（UserController、RoleController 等）—— 仅设计标注规范，不实现
2. DataScope 缓存优化 —— 初版不实现，按性能测试结果决定
3. `t_auth_permission_item` 表新增 `source_service` 字段 —— 后续管理后台需要时添加
4. PermissionScanner 强制模式切换 —— 初版保持宽松，待所有 Controller 标注完成后切换
5. 渠道-路径硬绑定 —— 遵循决策"仅功能权限判定"，不引入
