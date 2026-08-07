# permission-sdk 重构设计

> 日期：2026-08-07
> 主题：废弃 permission-sdk 零依赖路线，收口到 HttpExchange + 项目已有异常体系

## 一、背景与问题

### 1.1 原设计意图

`permission-sdk` 原设计为"零 Spring 依赖、纯 Java 接口 + 手工 HttpClient + 手工 JSON 解析"的权限客户端 SDK，供业务服务调用 auth-service 做权限校验。

### 1.2 实际问题

| 问题 | 描述 |
|------|------|
| 零依赖与项目技术栈不匹配 | 项目是 Spring Boot 多模块项目，无零依赖需求，手工 HttpClient 重复造轮子 |
| 三个 Http*Client 代码 80% 重复 | HttpPermissionClient、HttpPermissionCacheClient、HttpPermissionMetadataClient 的 `get()` 方法、异常处理逻辑完全相同，违反 DRY |
| 桩实现混入生产 | HttpPermissionCacheClient、HttpPermissionMetadataClient 返回 `emptySet()`，调用方无法区分"无权限"和"未实现" |
| 批量校验子串匹配 bug | HttpPermissionClient 用 `body.contains("key=true")` 判定，`CONTRIBUTION_VIEW=true` 会误判 `CONTRIBUTION=true` |
| CachingPermissionClient 内存泄漏 | 无 maxSize 上限、invalidate 前缀匹配有误删 bug（`U1` 误删 `U10`） |
| 注解两套包名并存 | `com.pension.permission.sdk.RequirePermission`（file-adapter）与 `com.pension.permission.api.annotation.RequirePermission`（approval/integration-adapter）并存 |
| SessionSignatureUtils 源文件缺失 | 被三处引用（demo-gateway、shared-permission-starter 两个类），但类文件不存在 |
| PermissionCheckApi 与 Controller 签名不一致 | API 用 `@GetExchange + @RequestParam`，Controller 用 Request 对象 |
| auth-api 包名不符合约定 | `com.pension.permission.api` 与项目 `com.example.xxx` 约定不一致 |
| 自定义异常不符合规范 | PermissionDeniedException 未实现 ErrorDefinition，违反 08-错误码规范 |

### 1.3 研究发现

通过深入调研 auth-service 的真实机制，发现：

1. **后端真实鉴权每次实时查 DB**：`EffectivePermissionService.checkPermission` 每次查 Grant + Assignment + RoleTemplate，权限变更天然立即生效，零缓存
2. **sa-token Token-Session 不存任何字段**：当前 `currentPermissions` 永远是 null，`SessionContextInjector` 读后不写入
3. **登录态与权限快照是两条独立轨道**：只有账号冻结才踢人，Grant/RoleTemplate/Assignment 变更不踢人
4. **已有半成品缓存骨架**：RedisPermissionCacheStore + GrantEventRefreshListener 已实现但未接线，定位是"前端可见性缓存"，不参与后端鉴权

## 二、核心决策

| 决策点 | 选择 | 理由 |
|------|------|------|
| API 风格 | 统一 POST + RequestBody | 与 approval-api/file-api 对齐，遵循 04-代码编写约束 |
| 包名 | 迁移到 com.example.auth.api | 与项目 com.example.xxx 约定一致 |
| SessionSignatureUtils 归属 | auth-api/util | 网关和业务服务共用，与权限会话同源 |
| 缓存架构 | 不缓存，后端实时鉴权 | 基于 EffectivePermissionService 真实机制，权限变更天然立即生效 |
| 异常体系 | BusinessException + PermissionErrorCode | 遵循项目已有异常体系，不自定义异常类 |
| RouteRule | 保留桩实现，不实现数据源 | YAGNI，网关降级安全，业务服务兜底 |
| permission-sdk | 删除 | 零依赖路线与项目技术栈不匹配 |

## 三、模块设计

### 3.1 模块边界

```
auth-service/
  auth-api/                    ← 收口：API 契约 + 注解 + 签名工具
    com.example.auth.api                        ← 5 个 API 接口
    com.example.auth.api.dto                     ← 15 个 DTO
    com.example.auth.api.command                 ← 9 个 Command
    com.example.auth.api.query                   ← 1 个 Query
    com.example.auth.api.annotation              ← @RequirePermission + PermissionCategory
    com.example.auth.api.util                    ← SessionSignatureUtils
  auth-adapter/                  ← 实现 5 个 API，包名统一
  auth-domain/...                ← 不变（EffectivePermissionService 实时鉴权）
  auth-application/...            ← 不变（PermissionQueryService 实时查 DB）
  auth-infrastructure/...         ← GrantEventRefreshListener 强化（可选）
  auth-starter/...                ← 不变
  permission-sdk/                 ← 删除

demo-shared/shared-starter/shared-permission-starter/   ← AOP 基础设施
  com.example.shared.permission                   ← 切面、SPI
  com.example.shared.permission.errorcode          ← PermissionErrorCode（SHARED.PERM）
```

### 3.2 各模块职责

| 模块 | 职责 | 依赖 |
|------|------|------|
| auth-api | API 接口、DTO、`@RequirePermission` 注解、`PermissionCategory` 枚举、`SessionSignatureUtils` | shared-api + auth-types + spring-web + jakarta.validation + lombok |
| shared-permission-starter | `RequirePermissionAspect` AOP 切面、`AccountIdResolver`/`PlanIdResolver`/`SessionContextSignatureVerifier` SPI + 默认实现、`PermissionErrorCode` 错误码、自动装配 | auth-api + spring-boot-starter-aop + spring-web |

### 3.3 关键设计原则

1. **`@RequirePermission` 放 auth-api/annotation**：注解是 API 契约的一部分，调用方引入 auth-api 即可使用注解，编译期不依赖 starter
2. **`SessionSignatureUtils` 放 auth-api/util**：被网关（WebFlux，签发）和业务服务（Servlet，验签）共用，网关已依赖 auth-api 不会带入 AOP 依赖
3. **异常类放 shared-permission-starter**：使用项目已有 BusinessException + 自定义 PermissionErrorCode，不自定义异常类
4. **permission-sdk 删除**：原零依赖路线与项目技术栈不匹配，CachingPermissionClient 缓存职责由"后端实时鉴权"方案替代

## 四、API 接口设计

### 4.1 统一风格

所有接口统一 `@HttpExchange` + `@PostExchange` + `@RequestBody @Valid`，返回 `ApiResult<T>`，与 approval-api/file-api 风格对齐。

### 4.2 API 清单

```java
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {
    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@RequestBody @Valid PermissionCheckBatchRequest request);
}

@HttpExchange("/route-rules")
public interface RouteRuleApi {
    @PostExchange("/list")
    ApiResult<List<RouteRuleResponse>> list(@RequestBody @Valid ListRouteRulesQuery query);
}

@HttpExchange("/permission-cache")
public interface PermissionCacheApi {
    @PostExchange("/platform")
    ApiResult<Set<PermissionResponse>> getPlatformPermissions(@RequestBody @Valid GetPlatformPermissionsRequest request);

    @PostExchange("/business")
    ApiResult<Set<PermissionResponse>> getBusinessPermissions(@RequestBody @Valid GetBusinessPermissionsRequest request);
}

@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {
    @PostExchange("/items")
    ApiResult<List<PermissionItemResponse>> listItems(@RequestBody @Valid ListPermissionItemsRequest request);

    @PostExchange("/items/grouped")
    ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestBody @Valid ListPermissionItemsRequest request);
}

@HttpExchange("/customer-channel-entitlement")
public interface CustomerChannelEntitlementApi {
    @PostExchange("/enable")
    ApiResult<CustomerChannelEntitlementResponse> enable(@RequestBody @Valid EnableChannelRequest request);

    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisableChannelRequest request);

    @PostExchange("/replace")
    ApiResult<CustomerChannelEntitlementResponse> replace(@RequestBody @Valid ReplaceChannelsRequest request);

    @PostExchange("/get")
    ApiResult<Optional<CustomerChannelEntitlementResponse>> get(@RequestBody @Valid GetEntitlementRequest request);
}
```

### 4.3 DTO 设计

```java
// command
public record PermissionCheckRequest(String accountId, String planId, String businessCode, String actionCode) {}
public record PermissionCheckBatchRequest(String accountId, String planId, List<PermissionCheckItemRequest> items) {}
public record PermissionCheckItemRequest(String businessCode, String actionCode) {}
public record GetPlatformPermissionsRequest(String accountId) {}
public record GetBusinessPermissionsRequest(String accountId, String planId) {}
public record ListPermissionItemsRequest(String category) {}
public record EnableChannelRequest(String customerNo, String channelType) {}
public record DisableChannelRequest(String customerNo, String channelType) {}
public record ReplaceChannelsRequest(String customerNo, List<String> channelTypes) {}
public record GetEntitlementRequest(String customerNo) {}

// query
public record ListRouteRulesQuery(String channelType, String checkType, Boolean enabled) {}

// response
public record PermissionCheckResponse(boolean allowed) {}
public record PermissionCheckBatchResponse(List<PermissionCheckItemResponse> items) {}
public record PermissionCheckItemResponse(String businessCode, String actionCode, boolean allowed) {}
public record PermissionResponse(String businessCode, String actionCode) {}
public record PermissionItemResponse(String businessCode, String actionCode, String category,
        String displayName, String description, String categoryGroup, int sortOrder) {}
public record PermissionGroupResponse(String groupName, List<PermissionItemResponse> items) {}
public record RouteRuleResponse(String routePattern, String checkType, String checkValue, int priority) {}
public record CustomerChannelEntitlementResponse(String customerNo, List<String> channelTypes, String status) {}
```

### 4.4 关键设计点

1. **统一 POST + RequestBody**：消除当前 `@GetExchange` + `@RequestParam` 的多参数 URL 风格
2. **批量校验返回结构化响应**：`PermissionCheckBatchResponse` 包含 `List<PermissionCheckItemResponse>`，彻底解决原 SDK `body.contains("key=true")` 的子串匹配 bug
3. **ListRouteRulesQuery 仍为 Query 命名**：遵循 CQE 约束，读操作用 Query 后缀

## 五、注解与签名工具设计

### 5.1 @RequirePermission 注解

```java
package com.example.auth.api.annotation;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String business();
    String action() default "";
    PermissionCategory category() default PermissionCategory.BUSINESS;
}
```

### 5.2 PermissionCategory 枚举

```java
package com.example.auth.api.annotation;

public enum PermissionCategory {
    BUSINESS,
    PLATFORM
}
```

### 5.3 SessionSignatureUtils

```java
package com.example.auth.api.util;

public final class SessionSignatureUtils {
    public static final String PAYLOAD_SEPARATOR = ":";
    public static final long DEFAULT_TTL_SECONDS = 300L;

    private SessionSignatureUtils() {}

    public static String sign(String payload, String secretKey);
    public static String buildAccountIdPayload(String loginId, long expireAtEpochSecond);
    public static SignedPayload signAccountId(String loginId, String secretKey);
    public static SignedPayload signAccountId(String loginId, String secretKey, long ttlSeconds);
    public static String signSessionContext(String sessionContextBase64, long expireAtEpochSecond, String secretKey);

    public static boolean verify(String payload, String signature, String secretKey);
    public static String verifyAccountId(String accountIdPayload, String signature, String secretKey);
    public static boolean verifySessionContext(String sessionContextBase64, String signature, long expireAtEpochSecond, String secretKey);

    private static boolean constantTimeEquals(String a, String b);
    private static String toHex(byte[] bytes);

    public record SignedPayload(String payload, String signature, long expireAtEpochSecond) {}
}
```

### 5.4 关键调整

| 原行为（permission-sdk） | 新行为（auth-api/util） | 理由 |
|------|------|------|
| `sign()` 在 secretKey 为空时返回空串 | 抛 `IllegalStateException("会话签名密钥未配置")` | 配置错误应 fail-fast |
| 包名 `com.pension.permission.sdk` | 包名 `com.example.auth.api.util` | 遵循项目约定 |

### 5.5 安全特性

| 特性 | 实现 |
|------|------|
| HMAC-SHA256 | JDK 内置 `javax.crypto.Mac`，无外部依赖 |
| 常量时间比较 | `constantTimeEquals` 防时序攻击 |
| 签名 + 过期双校验 | `verifyAccountId` 先验签再检查过期 |
| 零依赖达成 | 仅用 JDK 内置类，auth-api 本就依赖 spring-web，不引入新依赖 |

## 六、缓存架构与会话上下文透传

### 6.1 核心认知

| 维度 | 真实机制 |
|------|---------|
| 后端真实鉴权 | `EffectivePermissionService.checkPermission` 每次实时查 DB，权限变更天然立即生效，零缓存 |
| 前端可见性缓存 | 独立的 `SessionPermissionCache` 存于 Redis，TTL 5 分钟，仅供前端菜单/按钮显隐，不参与后端鉴权 |
| 登录态 | 与权限快照分离。只在账号冻结时踢人，Grant/RoleTemplate/Assignment 变更不踢人 |
| Token-Session | 当前不存 `currentPermissions`，`SessionContextInjector` 读后不写入 |

### 6.2 分层职责

```
后端真实鉴权层（auth-service）
  EffectivePermissionService.checkPermission
  每次实时查 DB + 解析 RoleTemplate，权限变更天然立即生效
  ❌ 不做任何缓存（设计意图）
        ↑
  业务服务 @RequirePermission 切面
  通过 PermissionCheckApi 实时调用
        ↑
网关透传层（demo-gateway）
  SessionContextInjector 透传 userNo/channelType/planNo
  ❌ 不透传 permissionCodes（后端实时鉴权，不需要）
  ✅ 透传 X-Account-Id + 签名
        ↑
前端可见性层（已有，保持现状）
  SessionPermissionCache（Redis, TTL 5min）
  PermissionCacheController 暴露 HTTP 接口供前端拉取
  GrantEventRefreshListener 监听 Grant 事件精确失效（UserListSubject）
```

### 6.3 关键决策

| 原方案（错误） | 修订方案（正确） | 理由 |
|------|------|------|
| 登录写 Token-Session.currentPermissions | 不写 | 后端实时鉴权，不需要缓存 |
| 网关透传 permissionCodes | 不透传 | 业务服务用过期权限码做安全判定有风险 |
| 业务服务短路读 permissionCodes | 删除 SessionContextShortCircuit | 与"后端实时鉴权"原则冲突 |
| Grant 变更 kickout 用户 | 不踢人 | 后端实时查 DB，权限变更立即生效；踢人破坏"登录态与权限分离"设计 |
| Redis 兜底缓存 | 保持现状，前端可见性缓存已有 | 不引入新的后端鉴权缓存层 |

### 6.4 SessionContext 透传内容精简

```json
{
  "userNo": "U001",
  "channelType": "BRANCH",
  "planNo": "P2026001"
}
```

删除 `permissionCodes` 字段。

### 6.5 fail-closed 原则

| 场景 | 行为 |
|------|------|
| 网关未透传 X-Session-Context | 业务服务仍可从 X-Account-Id 解析 accountId，实时调用 auth-service |
| X-Session-Context 签名验证失败 | 视为非法请求，拒绝 |
| auth-service 不可达 | 抛 BusinessException(PERMISSION_SERVICE_UNAVAILABLE)，拒绝请求 |
| auth-service 返回 allowed=false | 抛 BusinessException(PERMISSION_DENIED) |

## 七、shared-permission-starter 改造

### 7.1 模块定位

职责收窄为"AOP 切面 + 身份解析 SPI + 错误码 + 异常处理"。

| 原职责 | 新职责 | 说明 |
|------|------|------|
| RequirePermissionAspect | ✅ 保留 | 核心切面，实时调用 PermissionCheckApi |
| AccountIdResolver + DefaultAccountIdResolver | ✅ 保留 | 从 X-Account-Id header 解析，含验签 |
| PlanIdResolver + DefaultPlanIdResolver + PlanIdAware | ✅ 保留 | 从方法入参解析 planId |
| SessionContextSignatureVerifier + DefaultSessionContextSignatureVerifier | ✅ 保留 | 验签 X-Session-Context |
| SessionContextShortCircuit | ❌ 删除 | 不再有短路读需求 |
| PermissionClientAutoConfiguration | ❌ 删除 | 替换为 PermissionAutoConfiguration |
| PermissionProperties.cache / shortCircuit | ❌ 删除 | 不再有本地缓存 |
| PermissionDeniedException + PermissionServiceUnavailableException | ❌ 删除 | 改用 BusinessException + PermissionErrorCode |

### 7.2 文件清单

```
shared-permission-starter/src/main/java/com/example/shared/permission/
  PermissionAutoConfiguration.java
  RequirePermissionAspect.java
  PermissionProperties.java
  AccountIdResolver.java
  DefaultAccountIdResolver.java
  PlanIdResolver.java
  DefaultPlanIdResolver.java
  PlanIdAware.java
  SessionContextSignatureVerifier.java
  DefaultSessionContextSignatureVerifier.java
  errorcode/
    PermissionErrorCode.java          ← 新增错误码枚举

shared-permission-starter/src/main/resources/META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 7.3 PermissionErrorCode 错误码

错误码域分配：在 `08-错误码规范.md` 的 SHARED 域分配表中新增 `PERM`（shared-permission-starter）。

```java
package com.example.shared.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PermissionErrorCode implements ErrorDefinition {

    PERMISSION_DENIED("SHARED.PERM.0001", "无权限访问"),
    PERMISSION_SERVICE_UNAVAILABLE("SHARED.PERM.0002", "权限校验服务暂不可用"),
    SESSION_SIGNATURE_INVALID("SHARED.PERM.0003", "会话签名验证失败"),
    SESSION_CONTEXT_MISSING("SHARED.PERM.0004", "会话上下文缺失");

    private final String code;
    private final String message;
}
```

### 7.4 异常类型选择

| 场景 | 异常类型 | 错误码 | 理由 |
|------|------|------|------|
| 权限不足 | BusinessException | PERMISSION_DENIED | 业务规则阻断 |
| 服务不可达 | BusinessException | PERMISSION_SERVICE_UNAVAILABLE | fail-closed 是业务决策 |
| 签名验证失败 | BusinessException | SESSION_SIGNATURE_INVALID | 视为非法请求 |
| 会话上下文缺失 | BusinessException | SESSION_CONTEXT_MISSING | 无法解析操作者 |

**为什么不用 SystemException？** SystemException 语义是"系统错误、需要运维介入"，但权限校验失败（包括服务不可达的 fail-closed）都是业务决策的"拒绝访问"，不是系统故障。

### 7.5 PermissionProperties（精简）

```java
@Data
@ConfigurationProperties(prefix = "permission")
public class PermissionProperties {
    private SessionConfig session = new SessionConfig();

    @Data
    public static class SessionConfig {
        private String signatureKey = "";
    }
}
```

删除的配置项：
- `permission.service.*`（替换为 httpexchange 客户端配置）
- `permission.cache.*`
- `permission.short-circuit.*`

### 7.6 RequirePermissionAspect（简化）

```java
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RequirePermissionAspect {

    private final PermissionCheckApi permissionCheckApi;
    private final AccountIdResolver accountIdResolver;
    private final PlanIdResolver planIdResolver;

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        String accountId = accountIdResolver.resolve(joinPoint);
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("X-Account-Id header 缺失或验签失败")
                .withContext("business", requirePermission.business())
                .withContext("action", requirePermission.action());
        }

        String planId = planIdResolver.resolve(joinPoint, requirePermission);
        String businessCode = requirePermission.business();
        String actionCode = requirePermission.action().isBlank() ? null : requirePermission.action();

        ApiResult<PermissionCheckResponse> result;
        try {
            PermissionCheckRequest request = new PermissionCheckRequest(
                accountId, planId, businessCode, actionCode);
            result = permissionCheckApi.check(request);
        } catch (Exception e) {
            log.warn("[RequirePermission] 调用 auth-service 失败, fail-closed. account={}, business={}",
                accountId, businessCode, e);
            throw new BusinessException(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE, e)
                .withLogDetail("auth-service 调用异常: " + e.getMessage())
                .withContext("account", accountId)
                .withContext("business", businessCode);
        }

        if (result == null || !result.isSuccess() || result.data() == null) {
            throw new BusinessException(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE)
                .withLogDetail("auth-service 响应异常")
                .withContext("account", accountId)
                .withContext("business", businessCode);
        }
        if (!result.data().allowed()) {
            throw new BusinessException(PermissionErrorCode.PERMISSION_DENIED)
                .withContext("account", accountId)
                .withContext("plan", planId)
                .withContext("business", businessCode)
                .withContext("action", actionCode);
        }
        return joinPoint.proceed();
    }
}
```

### 7.7 自动装配

```java
@AutoConfiguration
@ConditionalOnClass(RequirePermission.class)
@EnableConfigurationProperties(PermissionProperties.class)
public class PermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AccountIdResolver accountIdResolver(PermissionProperties properties) {
        return new DefaultAccountIdResolver(properties.getSession().getSignatureKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public PlanIdResolver planIdResolver() {
        return new DefaultPlanIdResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "permission.session", name = "signature-key")
    public SessionContextSignatureVerifier sessionContextSignatureVerifier(
            PermissionProperties properties) {
        return new DefaultSessionContextSignatureVerifier(
            properties.getSession().getSignatureKey());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PermissionCheckApi.class)
    public RequirePermissionAspect requirePermissionAspect(
            PermissionCheckApi permissionCheckApi,
            AccountIdResolver accountIdResolver,
            PlanIdResolver planIdResolver) {
        return new RequirePermissionAspect(
            permissionCheckApi, accountIdResolver, planIdResolver);
    }
}
```

关键设计：
- `RequirePermissionAspect` 由 `@ConditionalOnBean(PermissionCheckApi.class)` 触发——业务服务必须配置 httpexchange 客户端才会装配切面
- `SessionContextSignatureVerifier` 由 `permission.session.signature-key` 触发——可选配置

## 八、auth-adapter 改造

### 8.1 改造范围

| Controller | 实现的 API | 改造内容 |
|------|------|------|
| PermissionCheckController | PermissionCheckApi | 重写为 POST + RequestBody |
| RouteRuleController | RouteRuleApi | 保持桩实现，包名迁移 |
| PermissionCacheController | PermissionCacheApi | 包名迁移，签名改为 POST |
| PermissionMetadataController | PermissionMetadataApi | 包名迁移，签名改为 POST |
| CustomerChannelEntitlementController | CustomerChannelEntitlementApi | 仅包名迁移 |

### 8.2 Controller 包结构统一

```
auth-adapter/src/main/java/com/example/auth/adapter/
  permission/
    PermissionCheckController.java
    PermissionCacheController.java
    PermissionMetadataController.java
  route/
    RouteRuleController.java
  channel/
    CustomerChannelEntitlementController.java
```

### 8.3 RouteRuleController 桩实现保留

RouteRule 是网关层动态鉴权的配置单元，用于实现"路由级粗粒度权限校验"。当前：
- 网关 RouteRuleLoader 5 分钟缓存 + 降级为仅登录校验
- Controller 返回空列表（TODO：接入 RouteRule 领域模型）
- 业务服务 @RequirePermission 兜底

本次保留桩实现，不实现数据源（YAGNI）。

## 九、调用方迁移

### 9.1 受影响的调用方

| 调用方 | 改造内容 |
|------|------|
| demo-gateway | 包名迁移 + 删除 permission-sdk 依赖 + SessionContextInjector 精简（删除 permissionCodes） |
| shared-permission-starter | 包名迁移 + RequirePermissionAspect 重写 + 删除 SessionContextShortCircuit + 删除自定义异常 |
| business-core-adapter | @RequirePermission import 迁移 |
| approval-adapter | @RequirePermission import 迁移 + 配置项调整 |
| file-adapter | @RequirePermission import 迁移 + 配置项调整 |
| integration-service-adapter | @RequirePermission import 迁移 + 配置项调整 |
| annuity-adapter | @RequirePermission import 迁移 + 配置项调整 |
| auth-infrastructure | PermissionScanner 调整（若依赖 SDK 类） |

### 9.2 业务服务统一配置

```yaml
# httpexchange 客户端配置（必须）
io:
  github:
    danielliu1123:
      httpexchange:
        clients:
          com.example.auth.api:
            url: lb://auth-service

# 权限校验配置（可选，仅启用签名验证时需要）
permission:
  session:
    signature-key: ${SESSION_SIGNATURE_KEY:}
```

```java
@EnableExchangeClients(basePackages = {"com.example.auth.api"})
@SpringBootApplication
public class XxxApplication { ... }
```

### 9.3 迁移顺序

1. 创建 auth-api 新包结构（com.example.auth.api.*）
2. 创建 shared-permission-starter 新实现
3. 改造 auth-adapter
4. 迁移 demo-gateway
5. 迁移 business-core-adapter + 4 个业务服务
6. 迁移 auth-infrastructure
7. 删除旧包 com.pension.permission.api
8. 删除 permission-sdk 模块
9. 全量编译验证 + 单元测试

## 十、测试策略

### 10.1 测试分层

| 层级 | 模块 | 覆盖重点 |
|------|------|------|
| 单元测试 | auth-api/util | SessionSignatureUtils 签名/验签、密钥缺失 fail-fast |
| 单元测试 | shared-permission-starter | RequirePermissionAspect 切面拦截、fail-closed、错误码映射 |
| 单元测试 | shared-permission-starter | DefaultAccountIdResolver header 解析、验签 |
| 单元测试 | shared-permission-starter | DefaultPlanIdResolver PlanIdAware 扫描、PLATFORM 类跳过 |
| 单元测试 | auth-adapter | PermissionCheckController API 签名适配、批量校验 |

### 10.2 关键测试用例

- SessionSignatureUtils：密钥为空抛 IllegalStateException、过期返回 null、错误密钥返回 false
- RequirePermissionAspect：账号缺失抛 SESSION_CONTEXT_MISSING、服务不可达抛 PERMISSION_SERVICE_UNAVAILABLE、allowed=false 抛 PERMISSION_DENIED、allowed=true 放行
- PermissionCheckController：批量校验返回结构化响应、planId 为 null 不抛异常

### 10.3 不测试的内容

| 不测试 | 理由 |
|------|------|
| PermissionCheckApi 接口本身 | 接口无逻辑 |
| PermissionProperties | 纯 POJO |
| PermissionErrorCode 枚举值 | 编译期固定 |
| RouteRuleController | 桩实现返回空列表 |

## 十一、权限校验完整流程

```
客户端 → demo-gateway
  ├─ sa-token 校验 token
  ├─ SessionContextInjector 透传 userNo/channelType/planNo + 签名
  └─ RouteRule 路由级校验（当前为空，降级为仅登录校验）
       ↓ X-Account-Id + X-Account-Sig + X-Session-Context + X-Session-Sig
业务服务 adapter
  └─ @RequirePermission AOP 切面拦截
      ├─ AccountIdResolver 从 X-Account-Id 解析（验签）
      ├─ PlanIdResolver 从 PlanIdAware 入参解析
      └─ PermissionCheckApi.check()（HttpExchange 代理）
          ↓ POST /internal/permissions/check
auth-service
  └─ PermissionCheckController → PermissionQueryService
      → EffectivePermissionService.checkPermission
          ├─ 能力层：grantRepository.findActiveCapabilityGrants（DB）
          ├─ 主体层：grantRepository.findCandidateSubjectGrants（DB）
          ├─ 角色模板：assignmentRepository + roleTemplateResolver（DB，实时解析）
          └─ EffectResolver 合并 DENY 优先
      ← boolean allowed
  ← ApiResult<PermissionCheckResponse>
  ← allowed=true 放行 / allowed=false 抛 BusinessException(PERMISSION_DENIED)
```

## 十二、关键不变量

1. **后端真实鉴权始终实时查 DB**——权限变更天然立即生效，不需要踢人或缓存失效
2. **登录态与权限快照分离**——Grant/RoleTemplate/Assignment 变更不踢人，只有账号冻结才踢人
3. **网关不透传权限码**——业务服务通过 PermissionCheckApi 实时调用，避免用过期权限码做安全判定
4. **fail-closed**——任何异常情况都拒绝访问，安全底线

## 十三、消除的重复与问题

| 原问题 | 解决方案 |
|------|------|
| permission-sdk 零依赖与项目 Spring Boot 不匹配 | 删除 SDK，走 HttpExchange |
| 三个 Http*Client 代码 80% 重复 | 删除，由 httpexchange-spring-boot-autoconfigure 装配代理 |
| 批量校验 body.contains 子串匹配 bug | 结构化 PermissionCheckBatchResponse |
| CachingPermissionClient 内存泄漏 + 前缀匹配 bug | 删除，后端实时鉴权 |
| @RequirePermission 注解两套包名并存 | 统一到 com.example.auth.api.annotation |
| SessionSignatureUtils 三处引用源文件缺失 | 收口到 auth-api/util |
| 自定义 PermissionDeniedException 不符合规范 | 改用 BusinessException + PermissionErrorCode |
| PermissionCheckApi 与 Controller 签名不一致 | 统一 POST + RequestBody |
| auth-api 包名 com.pension.permission.api 不符合约定 | 迁移到 com.example.auth.api |

## 十四、实施范围

| 阶段 | 内容 | 影响模块 |
|------|------|------|
| 1 | auth-api 新包结构 + 注解 + 签名工具 | auth-api |
| 2 | shared-permission-starter 重写 | shared-permission-starter |
| 3 | auth-adapter Controller 改造 | auth-adapter |
| 4 | demo-gateway 迁移 | demo-gateway |
| 5 | 业务服务迁移（4 个 + kernel） | approval/file/integration/annuity + business-core-adapter |
| 6 | auth-infrastructure 调整 | auth-infrastructure |
| 7 | 删除 permission-sdk + 旧包 | auth-service, 根 pom |
| 8 | 错误码规范文档更新 | .trae/rules/08-错误码规范.md |
| 9 | 全量编译验证 + 单元测试 | 全项目 |
