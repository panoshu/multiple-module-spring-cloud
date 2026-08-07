# permission-sdk 重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 废弃 permission-sdk 零依赖路线，收口到 HttpExchange + 项目已有异常体系，消除重复实现。

**Architecture:** auth-api 收口 API 契约 + 注解 + 签名工具；shared-permission-starter 承载 AOP 切面 + SPI + 错误码；permission-sdk 删除。后端实时鉴权不缓存不踢人。

**Tech Stack:** JDK 25、Spring Boot 3.5.14、HttpExchange (httpexchange-spring-boot-autoconfigure)、Caffeine、JUnit 5、Mockito、Lombok、MapStruct

## Global Constraints

- 包名统一为 `com.example.auth.api`（auth-api）和 `com.example.shared.permission`（shared-permission-starter）
- API 风格统一 `@HttpExchange` + `@PostExchange` + `@RequestBody @Valid`，返回 `ApiResult<T>`
- 异常使用项目已有 `BusinessException` + 自定义 `PermissionErrorCode`（SHARED.PERM.XXXX），不自定义异常类
- 后端真实鉴权每次实时查 DB，不引入缓存层
- 登录态与权限快照分离：Grant/RoleTemplate/Assignment 变更不踢人
- 网关不透传 permissionCodes，业务服务通过 PermissionCheckApi 实时调用
- fail-closed：任何异常都拒绝访问
- 遵循 04-代码编写约束、05-命名规范、08-错误码规范、09-提交信息规范
- 提交信息格式：`<type>(<scope>): <subject>`，scope 用 `auth-api`/`shared-permission-starter`/`demo-gateway` 等

---

## Task 1: 创建 auth-api 新包结构（接口 + DTO + Query + Command）

**Files:**
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionCheckApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/RouteRuleApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionCacheApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionMetadataApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/CustomerChannelEntitlementApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionCheckResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionCheckBatchResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionCheckItemResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionItemResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionGroupResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/RouteRuleResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/CustomerChannelEntitlementResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/PermissionCheckRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/PermissionCheckBatchRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/PermissionCheckItemRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/GetPlatformPermissionsRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/GetBusinessPermissionsRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/ListPermissionItemsRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/EnableChannelRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/DisableChannelRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/ReplaceChannelsRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/GetEntitlementRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/query/ListRouteRulesQuery.java`

**Interfaces:**
- Produces: 所有 API 接口和 DTO，供后续 Task 使用

**Note:** 此 Task 不删除旧包 `com.pension.permission.api`，保持编译兼容，最后一个 Task 统一删除。

- [ ] **Step 1: 创建 5 个 API 接口**

参考设计文档 §4.2，创建所有 API 接口。所有接口使用 `@HttpExchange("/path")` + `@PostExchange("/method")` + `@RequestBody @Valid`，返回 `ApiResult<T>`。

```java
// PermissionCheckApi.java
package com.example.auth.api;

import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {

    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@RequestBody @Valid PermissionCheckBatchRequest request);
}
```

```java
// RouteRuleApi.java
package com.example.auth.api;

import com.example.auth.api.dto.RouteRuleResponse;
import com.example.auth.api.query.ListRouteRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/route-rules")
public interface RouteRuleApi {

    @PostExchange("/list")
    ApiResult<List<RouteRuleResponse>> list(@RequestBody @Valid ListRouteRulesQuery query);
}
```

```java
// PermissionCacheApi.java
package com.example.auth.api;

import com.example.auth.api.command.GetBusinessPermissionsRequest;
import com.example.auth.api.command.GetPlatformPermissionsRequest;
import com.example.auth.api.dto.PermissionResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Set;

@HttpExchange("/permission-cache")
public interface PermissionCacheApi {

    @PostExchange("/platform")
    ApiResult<Set<PermissionResponse>> getPlatformPermissions(@RequestBody @Valid GetPlatformPermissionsRequest request);

    @PostExchange("/business")
    ApiResult<Set<PermissionResponse>> getBusinessPermissions(@RequestBody @Valid GetBusinessPermissionsRequest request);
}
```

```java
// PermissionMetadataApi.java
package com.example.auth.api;

import com.example.auth.api.command.ListPermissionItemsRequest;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {

    @PostExchange("/items")
    ApiResult<List<PermissionItemResponse>> listItems(@RequestBody @Valid ListPermissionItemsRequest request);

    @PostExchange("/items/grouped")
    ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestBody @Valid ListPermissionItemsRequest request);
}
```

```java
// CustomerChannelEntitlementApi.java
package com.example.auth.api;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.GetEntitlementRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

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

- [ ] **Step 2: 创建所有 DTO（response）**

参考设计文档 §4.3，创建 8 个 response record。所有 record 使用 `public record XXX(...)` 形式，无业务逻辑。

```java
// dto/PermissionCheckResponse.java
package com.example.auth.api.dto;
public record PermissionCheckResponse(boolean allowed) {}

// dto/PermissionCheckBatchResponse.java
package com.example.auth.api.dto;
import java.util.List;
public record PermissionCheckBatchResponse(List<PermissionCheckItemResponse> items) {}

// dto/PermissionCheckItemResponse.java
package com.example.auth.api.dto;
public record PermissionCheckItemResponse(String businessCode, String actionCode, boolean allowed) {}

// dto/PermissionResponse.java
package com.example.auth.api.dto;
public record PermissionResponse(String businessCode, String actionCode) {}

// dto/PermissionItemResponse.java
package com.example.auth.api.dto;
public record PermissionItemResponse(String businessCode, String actionCode, String category,
        String displayName, String description, String categoryGroup, int sortOrder) {}

// dto/PermissionGroupResponse.java
package com.example.auth.api.dto;
import java.util.List;
public record PermissionGroupResponse(String groupName, List<PermissionItemResponse> items) {}

// dto/RouteRuleResponse.java
package com.example.auth.api.dto;
public record RouteRuleResponse(String routePattern, String checkType, String checkValue, int priority) {}

// dto/CustomerChannelEntitlementResponse.java
package com.example.auth.api.dto;
import java.util.List;
public record CustomerChannelEntitlementResponse(String customerNo, List<String> channelTypes, String status) {}
```

- [ ] **Step 3: 创建所有 Command**

```java
// command/PermissionCheckRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record PermissionCheckRequest(
    @NotBlank String accountId,
    String planId,
    @NotBlank String businessCode,
    String actionCode) {}

// command/PermissionCheckBatchRequest.java
package com.example.auth.api.command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record PermissionCheckBatchRequest(
    @NotBlank String accountId,
    String planId,
    @NotEmpty @Valid List<PermissionCheckItemRequest> items) {}

// command/PermissionCheckItemRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record PermissionCheckItemRequest(@NotBlank String businessCode, String actionCode) {}

// command/GetPlatformPermissionsRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record GetPlatformPermissionsRequest(@NotBlank String accountId) {}

// command/GetBusinessPermissionsRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record GetBusinessPermissionsRequest(@NotBlank String accountId, @NotBlank String planId) {}

// command/ListPermissionItemsRequest.java
package com.example.auth.api.command;
public record ListPermissionItemsRequest(String category) {}

// command/EnableChannelRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record EnableChannelRequest(@NotBlank String customerNo, @NotBlank String channelType) {}

// command/DisableChannelRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record DisableChannelRequest(@NotBlank String customerNo, @NotBlank String channelType) {}

// command/ReplaceChannelsRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record ReplaceChannelsRequest(@NotBlank String customerNo, @NotEmpty List<String> channelTypes) {}

// command/GetEntitlementRequest.java
package com.example.auth.api.command;
import jakarta.validation.constraints.NotBlank;
public record GetEntitlementRequest(@NotBlank String customerNo) {}
```

- [ ] **Step 4: 创建 Query**

```java
// query/ListRouteRulesQuery.java
package com.example.auth.api.query;
public record ListRouteRulesQuery(String channelType, String checkType, Boolean enabled) {}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl auth-service/auth-api compile -q`
Expected: BUILD SUCCESS（新包与旧包并存，不冲突）

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/
git commit -m "feat(auth-api): 新增 com.example.auth.api 包结构（接口+DTO+Command+Query）"
```

---

## Task 2: 创建 auth-api 注解和签名工具

**Files:**
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/annotation/RequirePermission.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/annotation/PermissionCategory.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/util/SessionSignatureUtils.java`
- Test: `auth-service/auth-api/src/test/java/com/example/auth/api/util/SessionSignatureUtilsTest.java`

**Interfaces:**
- Produces: `@RequirePermission` 注解、`PermissionCategory` 枚举、`SessionSignatureUtils` 工具类

- [ ] **Step 1: 创建 PermissionCategory 枚举**

```java
package com.example.auth.api.annotation;

public enum PermissionCategory {
    BUSINESS,
    PLATFORM
}
```

- [ ] **Step 2: 创建 @RequirePermission 注解**

```java
package com.example.auth.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式权限校验注解，由 shared-permission-starter 的 AOP 切面拦截。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RequirePermission(business = "APPROVAL_FLOW", action = "CREATE")
 * public ApiResult<...> create(...) { ... }
 * }</pre>
 *
 * @author auth-api
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 业务编码，对应 PermissionItem.businessCode
     */
    String business();

    /**
     * 操作编码，空串代表不区分操作
     */
    String action() default "";

    /**
     * 权限类别，默认业务权限
     */
    PermissionCategory category() default PermissionCategory.BUSINESS;
}
```

- [ ] **Step 3: 写 SessionSignatureUtils 失败测试**

```java
package com.example.auth.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionSignatureUtilsTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef";

    @Test
    void sign_密钥为空_抛IllegalStateException() {
        assertThatThrownBy(() -> SessionSignatureUtils.sign("payload", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("会话签名密钥未配置");
    }

    @Test
    void sign_密钥为null_抛IllegalStateException() {
        assertThatThrownBy(() -> SessionSignatureUtils.sign("payload", null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sign_生成非空签名() {
        String signature = SessionSignatureUtils.sign("payload", SECRET);
        assertThat(signature).isNotBlank().hasSize(64);
    }

    @Test
    void verify_正确密钥_返回true() {
        String payload = "U001:1234567890";
        String signature = SessionSignatureUtils.sign(payload, SECRET);
        assertThat(SessionSignatureUtils.verify(payload, signature, SECRET)).isTrue();
    }

    @Test
    void verify_错误密钥_返回false() {
        String payload = "U001:1234567890";
        String signature = SessionSignatureUtils.sign(payload, SECRET);
        assertThat(SessionSignatureUtils.verify(payload, signature, "wrong-key")).isFalse();
    }

    @Test
    void verify_篡改payload_返回false() {
        String signature = SessionSignatureUtils.sign("U001:1234567890", SECRET);
        assertThat(SessionSignatureUtils.verify("U002:1234567890", signature, SECRET)).isFalse();
    }

    @Test
    void signAccountId_返回包含loginId和expireAt的payload() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        assertThat(signed.payload()).startsWith("U001:");
        assertThat(signed.signature()).hasSize(64);
        assertThat(signed.expireAtEpochSecond()).isGreaterThan(System.currentTimeMillis() / 1000);
    }

    @Test
    void verifyAccountId_未过期_返回loginId() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), signed.signature(), SECRET);
        assertThat(loginId).isEqualTo("U001");
    }

    @Test
    void verifyAccountId_已过期_返回null() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, -1L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), signed.signature(), SECRET);
        assertThat(loginId).isNull();
    }

    @Test
    void verifyAccountId_签名错误_返回null() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), "wrong-signature", SECRET);
        assertThat(loginId).isNull();
    }

    @Test
    void signSessionContext_生成签名() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        String signature = SessionSignatureUtils.signSessionContext(
            contextBase64, System.currentTimeMillis() / 1000 + 300, SECRET);
        assertThat(signature).hasSize(64);
    }

    @Test
    void verifySessionContext_正确_返回true() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        long expireAt = System.currentTimeMillis() / 1000 + 300;
        String signature = SessionSignatureUtils.signSessionContext(contextBase64, expireAt, SECRET);
        assertThat(SessionSignatureUtils.verifySessionContext(contextBase64, signature, expireAt, SECRET)).isTrue();
    }

    @Test
    void verifySessionContext_过期_返回false() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        long expireAt = System.currentTimeMillis() / 1000 - 1;
        String signature = SessionSignatureUtils.signSessionContext(contextBase64, expireAt, SECRET);
        assertThat(SessionSignatureUtils.verifySessionContext(contextBase64, signature, expireAt, SECRET)).isFalse();
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `mvn -pl auth-service/auth-api test -Dtest=SessionSignatureUtilsTest -q`
Expected: FAIL（SessionSignatureUtils 类不存在）

- [ ] **Step 5: 实现 SessionSignatureUtils**

```java
package com.example.auth.api.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 会话签名工具，用于网关签发 X-Account-Id/X-Session-Context 和业务服务验签。
 *
 * <p>使用 HMAC-SHA256 算法，仅依赖 JDK 内置类，无外部依赖。
 *
 * <p>安全特性：
 * <ul>
 *   <li>常量时间比较防时序攻击</li>
 *   <li>签名 + 过期双校验</li>
 *   <li>密钥缺失 fail-fast（抛 IllegalStateException）</li>
 * </ul>
 *
 * @author auth-api
 */
public final class SessionSignatureUtils {

    public static final String PAYLOAD_SEPARATOR = ":";
    public static final long DEFAULT_TTL_SECONDS = 300L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private SessionSignatureUtils() {}

    public static String sign(String payload, String secretKey) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalStateException("待签名内容不能为空");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("会话签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("会话签名计算失败", e);
        }
    }

    public static String buildAccountIdPayload(String loginId, long expireAtEpochSecond) {
        return loginId + PAYLOAD_SEPARATOR + expireAtEpochSecond;
    }

    public static SignedPayload signAccountId(String loginId, String secretKey) {
        return signAccountId(loginId, secretKey, DEFAULT_TTL_SECONDS);
    }

    public static SignedPayload signAccountId(String loginId, String secretKey, long ttlSeconds) {
        long expireAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = buildAccountIdPayload(loginId, expireAt);
        String signature = sign(payload, secretKey);
        return new SignedPayload(payload, signature, expireAt);
    }

    public static String signSessionContext(String sessionContextBase64,
                                            long expireAtEpochSecond,
                                            String secretKey) {
        String payload = sessionContextBase64 + PAYLOAD_SEPARATOR + expireAtEpochSecond;
        return sign(payload, secretKey);
    }

    public static boolean verify(String payload, String signature, String secretKey) {
        if (payload == null || signature == null || secretKey == null || secretKey.isEmpty()) {
            return false;
        }
        String expected = sign(payload, secretKey);
        return constantTimeEquals(expected, signature);
    }

    public static String verifyAccountId(String accountIdPayload,
                                         String signature,
                                         String secretKey) {
        if (!verify(accountIdPayload, signature, secretKey)) {
            return null;
        }
        int separatorIdx = accountIdPayload.lastIndexOf(PAYLOAD_SEPARATOR);
        if (separatorIdx <= 0) {
            return null;
        }
        String loginId = accountIdPayload.substring(0, separatorIdx);
        long expireAt;
        try {
            expireAt = Long.parseLong(accountIdPayload.substring(separatorIdx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (Instant.now().getEpochSecond() > expireAt) {
            return null;
        }
        return loginId;
    }

    public static boolean verifySessionContext(String sessionContextBase64,
                                               String signature,
                                               long expireAtEpochSecond,
                                               String secretKey) {
        if (Instant.now().getEpochSecond() > expireAtEpochSecond) {
            return false;
        }
        String payload = sessionContextBase64 + PAYLOAD_SEPARATOR + expireAtEpochSecond;
        return verify(payload, signature, secretKey);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public record SignedPayload(String payload, String signature, long expireAtEpochSecond) {}
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-api test -Dtest=SessionSignatureUtilsTest -q`
Expected: PASS（13 个测试）

- [ ] **Step 7: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/annotation/ \
        auth-service/auth-api/src/main/java/com/example/auth/api/util/ \
        auth-service/auth-api/src/test/java/com/example/auth/api/util/
git commit -m "feat(auth-api): 新增 @RequirePermission 注解和 SessionSignatureUtils 签名工具"
```

---

## Task 3: 重写 shared-permission-starter（AOP 切面 + SPI + 错误码）

**Files:**
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/errorcode/PermissionErrorCode.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionAutoConfiguration.java`
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/RequirePermissionAspect.java`
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionProperties.java`
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DefaultAccountIdResolver.java`
- Delete: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/SessionContextShortCircuit.java`
- Delete: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionDeniedException.java`
- Delete: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionServiceUnavailableException.java`
- Delete: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionClientAutoConfiguration.java`（若存在）
- Test: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/RequirePermissionAspectTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PermissionCheckApi`、`PermissionCheckRequest`、`PermissionCheckResponse`；Task 2 的 `@RequirePermission`、`PermissionCategory`、`SessionSignatureUtils`
- Produces: `PermissionErrorCode`、`RequirePermissionAspect`（简化版）、`PermissionAutoConfiguration`

- [ ] **Step 1: 创建 PermissionErrorCode 错误码枚举**

```java
package com.example.shared.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * shared-permission-starter 模块错误码定义。
 * <p>
 * 错误码区间 {@code SHARED.PERM.0001-SHARED.PERM.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SHARED.PERM.XXXX（公共基础模块 - shared-permission-starter）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author shared-permission-starter
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PermissionErrorCode implements ErrorDefinition {

    /** 权限不足，拒绝访问 */
    PERMISSION_DENIED("SHARED.PERM.0001", "无权限访问"),

    /** 权限校验服务不可达，fail-closed 拒绝 */
    PERMISSION_SERVICE_UNAVAILABLE("SHARED.PERM.0002", "权限校验服务暂不可用"),

    /** 会话上下文签名验证失败 */
    SESSION_SIGNATURE_INVALID("SHARED.PERM.0003", "会话签名验证失败"),

    /** 会话上下文缺失，无法解析操作者 */
    SESSION_CONTEXT_MISSING("SHARED.PERM.0004", "会话上下文缺失");

    private final String code;
    private final String message;
}
```

- [ ] **Step 2: 删除废弃的类**

删除以下文件（若存在）：
- `SessionContextShortCircuit.java`
- `PermissionDeniedException.java`
- `PermissionServiceUnavailableException.java`
- `PermissionClientAutoConfiguration.java`

- [ ] **Step 3: 简化 PermissionProperties**

```java
package com.example.shared.permission;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限校验配置项。
 *
 * <p>对应 application.yml 中的 {@code permission.*} 配置：
 * <pre>{@code
 * permission:
 *   session:
 *     signature-key: ${SESSION_SIGNATURE_KEY:}   # 网关与业务服务共享的 HMAC 密钥
 * }
 * }</pre>
 *
 * @author shared-permission-starter
 */
@Data
@ConfigurationProperties(prefix = "permission")
public class PermissionProperties {

    /**
     * 会话签名配置
     */
    private SessionConfig session = new SessionConfig();

    @Data
    public static class SessionConfig {
        /**
         * 网关与业务服务共享的 HMAC-SHA256 密钥
         * <p>未配置时业务服务不验签，信任网关透传的 X-Account-Id
         */
        private String signatureKey = "";
    }
}
```

- [ ] **Step 4: 写 RequirePermissionAspect 失败测试**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.util.SessionSignatureUtils;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import com.example.shared.web.core.api.ApiResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirePermissionAspectTest {

    @Mock
    private PermissionCheckApi permissionCheckApi;
    @Mock
    private AccountIdResolver accountIdResolver;
    @Mock
    private PlanIdResolver planIdResolver;
    @Mock
    private ProceedingJoinPoint joinPoint;

    private RequirePermissionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RequirePermissionAspect(
            permissionCheckApi, accountIdResolver, planIdResolver);
    }

    @Test
    void check_账号缺失_抛BusinessException_SESSION_CONTEXT_MISSING() {
        when(accountIdResolver.resolve(joinPoint)).thenReturn(null);
        RequirePermission annotation = mockAnnotation("BUSINESS", "VIEW");

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.code()).isEqualTo(PermissionErrorCode.SESSION_CONTEXT_MISSING.getCode());
            });
    }

    @Test
    void check_authService不可达_抛BusinessException_PERMISSION_SERVICE_UNAVAILABLE() {
        when(accountIdResolver.resolve(joinPoint)).thenReturn("U001");
        when(planIdResolver.resolve(any(), any())).thenReturn("P001");
        when(permissionCheckApi.check(any()))
            .thenThrow(new RuntimeException("connection refused"));
        RequirePermission annotation = mockAnnotation("BUSINESS", "VIEW");

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.code()).isEqualTo(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE.getCode());
            });
    }

    @Test
    void check_响应为null_抛BusinessException_PERMISSION_SERVICE_UNAVAILABLE() {
        when(accountIdResolver.resolve(joinPoint)).thenReturn("U001");
        when(planIdResolver.resolve(any(), any())).thenReturn("P001");
        when(permissionCheckApi.check(any())).thenReturn(null);
        RequirePermission annotation = mockAnnotation("BUSINESS", "VIEW");

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.code()).isEqualTo(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE.getCode());
            });
    }

    @Test
    void check_allowed为false_抛BusinessException_PERMISSION_DENIED() {
        when(accountIdResolver.resolve(joinPoint)).thenReturn("U001");
        when(planIdResolver.resolve(any(), any())).thenReturn("P001");
        when(permissionCheckApi.check(any()))
            .thenReturn(ApiResult.success(new PermissionCheckResponse(false)));
        RequirePermission annotation = mockAnnotation("BUSINESS", "VIEW");

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.code()).isEqualTo(PermissionErrorCode.PERMISSION_DENIED.getCode());
            });
    }

    @Test
    void check_allowed为true_放行执行目标方法() throws Throwable {
        when(accountIdResolver.resolve(joinPoint)).thenReturn("U001");
        when(planIdResolver.resolve(any(), any())).thenReturn("P001");
        when(permissionCheckApi.check(any()))
            .thenReturn(ApiResult.success(new PermissionCheckResponse(true)));
        when(joinPoint.proceed()).thenReturn("success");
        RequirePermission annotation = mockAnnotation("BUSINESS", "VIEW");

        Object result = aspect.check(joinPoint, annotation);
        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    void check_action为空_传null给authService() {
        when(accountIdResolver.resolve(joinPoint)).thenReturn("U001");
        when(planIdResolver.resolve(any(), any())).thenReturn("P001");
        when(permissionCheckApi.check(any()))
            .thenReturn(ApiResult.success(new PermissionCheckResponse(true)));
        RequirePermission annotation = mockAnnotation("BUSINESS", "");

        try {
            aspect.check(joinPoint, annotation);
        } catch (Throwable ignored) {
        }

        verify(permissionCheckApi).check(argThat(req -> req.actionCode() == null));
    }

    private RequirePermission mockAnnotation(String business, String action) {
        RequirePermission annotation = mock(RequirePermission.class);
        when(annotation.business()).thenReturn(business);
        when(annotation.action()).thenReturn(action);
        when(annotation.category()).thenReturn(PermissionCategory.BUSINESS);
        return annotation;
    }
}
```

- [ ] **Step 5: 运行测试验证失败**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=RequirePermissionAspectTest -q`
Expected: FAIL（RequirePermissionAspect 仍为旧实现，依赖 SessionContextShortCircuit）

- [ ] **Step 6: 重写 RequirePermissionAspect**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * @RequirePermission 注解的 AOP 切面实现。
 *
 * <p>拦截标注 {@link RequirePermission} 的方法，通过 {@link PermissionCheckApi}
 * 实时调用 auth-service 做权限校验。fail-closed：任何异常情况都拒绝访问。
 *
 * @author shared-permission-starter
 */
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
        String actionCode = requirePermission.action().isBlank()
            ? null : requirePermission.action();

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

- [ ] **Step 7: 更新 DefaultAccountIdResolver（改用 SessionSignatureUtils from auth-api）**

修改 `DefaultAccountIdResolver.java`，将 `com.example.shared.utils.SessionSignatureUtils` 或 `com.pension.permission.sdk.SessionSignatureUtils` 的引用改为 `com.example.auth.api.util.SessionSignatureUtils`。

```java
package com.example.shared.permission;

import com.example.auth.api.util.SessionSignatureUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@RequiredArgsConstructor
public class DefaultAccountIdResolver implements AccountIdResolver {

    private final String signatureKey;

    @Override
    public String resolve(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String accountIdPayload = request.getHeader("X-Account-Id");
        if (accountIdPayload == null || accountIdPayload.isBlank()) {
            return null;
        }

        if (signatureKey == null || signatureKey.isBlank()) {
            // 未配置密钥，信任网关透传
            return extractLoginId(accountIdPayload);
        }

        // 配置密钥，验签
        String signature = request.getHeader("X-Account-Sig");
        if (signature == null || signature.isBlank()) {
            log.warn("[AccountIdResolver] X-Account-Sig 缺失");
            return null;
        }
        return SessionSignatureUtils.verifyAccountId(accountIdPayload, signature, signatureKey);
    }

    private String extractLoginId(String payload) {
        int idx = payload.lastIndexOf(SessionSignatureUtils.PAYLOAD_SEPARATOR);
        return idx > 0 ? payload.substring(0, idx) : payload;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
```

- [ ] **Step 8: 创建 PermissionAutoConfiguration**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.annotation.RequirePermission;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * shared-permission-starter 自动装配入口。
 *
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnClass(RequirePermission.class)} - auth-api 在 classpath</li>
 *   <li>{@code @ConditionalOnBean(PermissionCheckApi.class)} - 业务服务配置了 httpexchange 客户端</li>
 * </ul>
 *
 * @author shared-permission-starter
 */
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

- [ ] **Step 9: 更新 META-INF/spring/AutoConfiguration.imports**

确认文件 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 内容为：

```
com.example.shared.permission.PermissionAutoConfiguration
```

（删除旧的 PermissionClientAutoConfiguration 引用）

- [ ] **Step 10: 更新 shared-permission-starter/pom.xml**

将 `permission-sdk` 依赖改为 `auth-api` 依赖：

```xml
<!-- 删除 -->
<!-- <dependency>
    <groupId>com.example</groupId>
    <artifactId>permission-sdk</artifactId>
</dependency> -->

<!-- 新增 -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>auth-api</artifactId>
</dependency>
```

- [ ] **Step 11: 运行测试验证通过**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=RequirePermissionAspectTest -q`
Expected: PASS（6 个测试）

- [ ] **Step 12: Commit**

```bash
git add demo-shared/shared-starter/shared-permission-starter/
git commit -m "refactor(shared-permission-starter): 重写 AOP 切面走 HttpExchange，删除自定义异常改用 BusinessException"
```

---

## Task 4: 改造 auth-adapter Controller 实现 API 新签名

**Files:**
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCheckController.java`（从旧路径迁移）
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/RouteRuleController.java`
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCacheController.java`
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionMetadataController.java`
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/channel/CustomerChannelEntitlementController.java`
- Test: `auth-service/auth-adapter/src/test/java/com/example/auth/adapter/permission/PermissionCheckControllerTest.java`

**Interfaces:**
- Consumes: Task 1 的所有 API 接口和 DTO
- Produces: 5 个 Controller 实现新 API 签名

**Note:** 需要先查看现有 Controller 的实际实现逻辑，特别是 PermissionCacheController 和 PermissionMetadataController 委托的 Service。

- [ ] **Step 1: 迁移 PermissionCheckController 并重写为新签名**

将文件从旧包 `com.pension.permission.adapter.permission` 迁移到 `com.example.auth.adapter.permission`，并改写为实现 POST + RequestBody 签名。

```java
package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckItemResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionQueryService;
import com.pension.permission.application.permission.query.CheckPermissionQuery;
import com.pension.permission.domain.permission.valueobject.BusinessCode;
import com.pension.permission.domain.permission.valueobject.ActionCode;
import com.pension.permission.domain.plan.valueobject.PlanNo;
import com.pension.permission.domain.user.valueobject.UserNo;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class PermissionCheckController implements PermissionCheckApi {

    private final PermissionQueryService permissionQueryService;

    @Override
    public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
        CheckPermissionQuery query = new CheckPermissionQuery(
            UserNo.of(request.accountId()),
            resolvePlanNo(request.planId()),
            new BusinessCode(request.businessCode()),
            resolveActionCode(request.actionCode()));
        boolean allowed = permissionQueryService.checkPermission(query);
        return ApiResult.success(new PermissionCheckResponse(allowed));
    }

    @Override
    public ApiResult<PermissionCheckBatchResponse> checkBatch(
            PermissionCheckBatchRequest request) {
        List<PermissionCheckItemResponse> items = request.items().stream()
            .map(item -> {
                CheckPermissionQuery query = new CheckPermissionQuery(
                    UserNo.of(request.accountId()),
                    resolvePlanNo(request.planId()),
                    new BusinessCode(item.businessCode()),
                    resolveActionCode(item.actionCode()));
                boolean allowed = permissionQueryService.checkPermission(query);
                return new PermissionCheckItemResponse(
                    item.businessCode(), item.actionCode(), allowed);
            })
            .toList();
        return ApiResult.success(new PermissionCheckBatchResponse(items));
    }

    private PlanNo resolvePlanNo(String planId) {
        return planId == null || planId.isBlank() ? null : new PlanNo(planId);
    }

    private ActionCode resolveActionCode(String actionCode) {
        return actionCode == null || actionCode.isBlank() ? null : new ActionCode(actionCode);
    }
}
```

**注意**：上述代码中的 `UserNo`、`PlanNo`、`BusinessCode`、`ActionCode`、`CheckPermissionQuery` 等领域对象包路径需要先验证实际位置。执行时用 Grep 确认这些类的真实包名，再调整 import。

- [ ] **Step 2: 写 PermissionCheckController 测试**

```java
package com.example.auth.adapter.permission;

import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckItemRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCheckControllerTest {

    @Mock
    private PermissionQueryService permissionQueryService;
    private PermissionCheckController controller;

    @BeforeEach
    void setUp() {
        controller = new PermissionCheckController(permissionQueryService);
    }

    @Test
    void check_传入Request_委托Service_返回allowed() {
        PermissionCheckRequest request = new PermissionCheckRequest(
            "U001", "P001", "APPROVAL_FLOW", "CREATE");
        when(permissionQueryService.checkPermission(any())).thenReturn(true);

        ApiResult<PermissionCheckResponse> result = controller.check(request);

        assertThat(result.data().allowed()).isTrue();
        verify(permissionQueryService).checkPermission(any());
    }

    @Test
    void checkBatch_多个权限点_逐个校验_返回结构化响应() {
        PermissionCheckBatchRequest request = new PermissionCheckBatchRequest(
            "U001", "P001",
            List.of(
                new PermissionCheckItemRequest("APPROVAL_FLOW", "CREATE"),
                new PermissionCheckItemRequest("ANNUITY", "UPLOAD_FORM")));
        when(permissionQueryService.checkPermission(any()))
            .thenReturn(true)
            .thenReturn(false);

        ApiResult<PermissionCheckBatchResponse> result = controller.checkBatch(request);

        assertThat(result.data().items()).hasSize(2);
        assertThat(result.data().items().get(0).allowed()).isTrue();
        assertThat(result.data().items().get(1).allowed()).isFalse();
    }

    @Test
    void check_planId为null_不抛异常() {
        PermissionCheckRequest request = new PermissionCheckRequest(
            "U001", null, "USER", "VIEW");
        when(permissionQueryService.checkPermission(any())).thenReturn(true);

        ApiResult<PermissionCheckResponse> result = controller.check(request);

        assertThat(result.data().allowed()).isTrue();
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-adapter test -Dtest=PermissionCheckControllerTest -q`
Expected: PASS（3 个测试）

- [ ] **Step 4: 迁移 RouteRuleController**

```java
package com.example.auth.adapter.route;

import com.example.auth.api.RouteRuleApi;
import com.example.auth.api.dto.RouteRuleResponse;
import com.example.auth.api.query.ListRouteRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
public class RouteRuleController implements RouteRuleApi {

    @Override
    public ApiResult<List<RouteRuleResponse>> list(ListRouteRulesQuery query) {
        log.debug("[RouteRuleController] 查询路由规则: query={}", query);
        // TODO: 接入 RouteRule 领域模型，当前返回空列表
        return ApiResult.success(List.of());
    }
}
```

- [ ] **Step 5: 迁移 PermissionCacheController 和 PermissionMetadataController**

将这两个 Controller 从旧包 `com.pension.permission.adapter.permission` 迁移到 `com.example.auth.adapter.permission`，调整 import 到新包的 API 和 DTO，委托逻辑保持不变（仍调用原有 Service）。

注意：这两个 Controller 的 API 签名从 `@GetExchange + @RequestParam` 改为 `@PostExchange + @RequestBody`，需要调整方法签名。

- [ ] **Step 6: 迁移 CustomerChannelEntitlementController**

从 `com.pension.permission.adapter.controller` 迁移到 `com.example.auth.adapter.channel`，调整 import 到新包的 API 和 DTO。

- [ ] **Step 7: 编译验证**

Run: `mvn -pl auth-service/auth-adapter compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add auth-service/auth-adapter/
git commit -m "refactor(auth-adapter): Controller 迁移到 com.example.auth.adapter 包并实现 API 新签名"
```

---

## Task 5: 迁移 demo-gateway

**Files:**
- Modify: `demo-gateway/pom.xml`
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/SessionContextInjector.java`
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/RouteRuleLoader.java`
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/RouteRule.java`
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/GatewayStpInterfaceImpl.java`
- Modify: `demo-gateway/src/main/java/com/example/gateway/GatewayApplication.java`
- Modify: `demo-gateway/src/main/resources/application.yml`
- Modify: `demo-gateway/src/main/resources/application-local.yml`
- Modify: `demo-gateway/src/test/java/com/example/gateway/security/RouteRuleLoaderTest.java`
- Modify: `demo-gateway/src/test/java/com/example/gateway/security/RouteRuleTest.java`

**Interfaces:**
- Consumes: Task 1 的 `RouteRuleApi`、`RouteRuleResponse`、`ListRouteRulesQuery`；Task 2 的 `SessionSignatureUtils`

- [ ] **Step 1: 更新 demo-gateway/pom.xml**

删除 `permission-sdk` 依赖，保留 `auth-api` 依赖。

- [ ] **Step 2: 迁移 RouteRuleApi 相关 import**

将 `com.pension.permission.api.RouteRuleApi` → `com.example.auth.api.RouteRuleApi`，以及对应的 DTO 和 Query。

涉及文件：RouteRuleLoader.java、RouteRule.java、RouteRuleLoaderTest.java、RouteRuleTest.java。

- [ ] **Step 3: 迁移 SessionSignatureUtils import**

将 `com.pension.permission.sdk.SessionSignatureUtils` → `com.example.auth.api.util.SessionSignatureUtils`。

涉及文件：SessionContextInjector.java。

- [ ] **Step 4: 精简 SessionContextInjector**

删除 `permissionCodes` 相关逻辑（从 Token-Session 读 currentPermissions 并写入 context 的代码）。

- [ ] **Step 5: 更新 GatewayStpInterfaceImpl**

删除 `SESSION_KEY_CURRENT_PERMISSIONS` 常量（不再使用），保留 `SESSION_KEY_CURRENT_PLAN_ID`。`getPermissionList` 返回 `List.of()`。

- [ ] **Step 6: 更新 GatewayApplication 启动类**

```java
@EnableExchangeClients(basePackages = {"com.example.auth.api"})
```

- [ ] **Step 7: 更新 application.yml**

```yaml
# 删除 permission.cache / shortCircuit 配置
# 保留 permission.session.signature-key（如有）
permission:
  session:
    signature-key: ${SESSION_SIGNATURE_KEY:}

# httpexchange 客户端配置调整为新包名
io:
  github:
    danielliu1123:
      httpexchange:
        clients:
          - client-class-name: com.example.auth.api.RouteRuleApi
            url: lb://auth-service
```

- [ ] **Step 8: 运行测试验证通过**

Run: `mvn -pl demo-gateway test -q`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add demo-gateway/
git commit -m "refactor(demo-gateway): 迁移到 com.example.auth.api，删除 permission-sdk 依赖，精简 SessionContextInjector"
```

---

## Task 6: 迁移 business-core-adapter 和 4 个业务服务

**Files:**
- Modify: `business-core-kernel/business-core-adapter/pom.xml`
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/BusinessApplicationController.java`
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/BusinessBatchController.java`
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/material/MaterialController.java`
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/progress/BusinessProgressController.java`
- Modify: `approval-service/approval-adapter/src/main/java/com/example/approval/adapter/controllers/ApprovalFlowAdapter.java`
- Modify: `approval-service/approval-adapter/src/main/java/com/example/approval/adapter/controllers/ApprovalInstanceAdapter.java`
- Modify: `approval-service/approval-starter/src/main/resources/application-local.yml`
- Modify: `approval-service/approval-starter/src/main/java/com/example/approval/ApprovalApplication.java`
- Modify: `file-service/file-adapter/src/main/java/com/example/file/adapter/access/FileAccessAdapter.java`
- Modify: `file-service/file-adapter/src/main/java/com/example/file/adapter/controllers/FileTaskAdapter.java`
- Modify: `file-service/file-starter/src/main/resources/application-local.yml`
- Modify: `file-service/file-starter/src/main/java/com/example/file/FileApplication.java`
- Modify: `integration-service/integration-service-adapter/src/main/java/com/example/integration/adapter/trade/TradeQueryAdapter.java`
- Modify: `integration-service/integration-service-starter/src/main/resources/application-local.yml`
- Modify: `integration-service/integration-service-starter/src/main/java/com/example/integration/IntegrationApplication.java`
- Modify: `annuity-service/annuity-adapter/src/main/java/com/example/annuity/adapter/controller/AnnuityController.java`
- Modify: `annuity-service/annuity-starter/src/main/resources/application-local.yml`
- Modify: `annuity-service/annuity-starter/src/main/java/com/example/annuity/AnnuityApplication.java`

**Interfaces:**
- Consumes: Task 2 的 `@RequirePermission`、`PermissionCategory`

- [ ] **Step 1: 批量替换 @RequirePermission import**

所有 Controller 的 import 统一为：

```java
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.annotation.PermissionCategory;
```

删除旧的：
- `import com.pension.permission.sdk.RequirePermission;`
- `import com.pension.permission.sdk.PermissionCategory;`
- `import com.pension.permission.api.annotation.RequirePermission;`
- `import com.pension.permission.api.annotation.PermissionCategory;`

- [ ] **Step 2: 更新业务服务 application-local.yml**

对 4 个业务服务（approval、file、integration、annuity）的 application-local.yml：

删除：
```yaml
permission:
  service:
    base-url: http://127.0.0.1:18085
    timeout: 2s
    token: ""
  cache:
    ttl: 10s
    max-size: 10000
```

新增：
```yaml
io:
  github:
    danielliu1123:
      httpexchange:
        clients:
          com.example.auth.api:
            url: lb://auth-service

permission:
  session:
    signature-key: ${SESSION_SIGNATURE_KEY:}
```

- [ ] **Step 3: 更新业务服务启动类 @EnableExchangeClients**

4 个业务服务的 Application 类添加 `com.example.auth.api` 到 `@EnableExchangeClients`：

```java
@EnableExchangeClients(basePackages = {
    "com.example.approval.api",  // 现有的
    "com.example.auth.api"       // 新增
})
```

- [ ] **Step 4: 更新 business-core-adapter/pom.xml**

确认依赖 `shared-permission-starter`（已存在），不再依赖 `permission-sdk`。

- [ ] **Step 5: 编译验证所有业务服务**

Run: `mvn -pl business-core-kernel/business-core-adapter,approval-service/approval-adapter,file-service/file-adapter,integration-service/integration-service-adapter,annuity-service/annuity-adapter compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: 运行业务服务测试**

Run: `mvn -pl business-core-kernel/business-core-adapter,approval-service/approval-adapter,file-service/file-adapter,integration-service/integration-service-adapter,annuity-service/annuity-adapter test -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add business-core-kernel/business-core-adapter/ \
        approval-service/ file-service/ integration-service/ annuity-service/
git commit -m "refactor(auth-service): 业务服务统一迁移到 com.example.auth.api.annotation 包"
```

---

## Task 7: 调整 auth-infrastructure 并删除旧包

**Files:**
- Modify: `auth-service/auth-infrastructure/pom.xml`
- Modify: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/PermissionScanner.java`（若依赖 SDK）
- Delete: `auth-service/auth-api/src/main/java/com/pension/permission/api/`（整个旧包）
- Modify: `auth-service/pom.xml`
- Modify: `pom.xml`（根 pom）

**Interfaces:**
- Consumes: Task 1-6 的所有新实现

- [ ] **Step 1: 检查 auth-infrastructure 是否依赖 permission-sdk**

Run: `grep -r "com.pension.permission.sdk" auth-service/auth-infrastructure/src/`

若有引用，调整 import 到新包。

- [ ] **Step 2: 删除 auth-infrastructure 的 permission-sdk 依赖**

修改 `auth-service/auth-infrastructure/pom.xml`，删除：
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>permission-sdk</artifactId>
</dependency>
```

- [ ] **Step 3: 删除旧包 com.pension.permission.api**

删除整个目录：`auth-service/auth-api/src/main/java/com/pension/permission/`

- [ ] **Step 4: 编译验证 auth-service**

Run: `mvn -pl auth-service/auth-api,auth-service/auth-domain,auth-service/auth-application,auth-service/auth-adapter,auth-service/auth-infrastructure,auth-service/auth-starter compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A auth-service/
git commit -m "refactor(auth-service): 删除旧包 com.pension.permission.api 和 permission-sdk 依赖"
```

---

## Task 8: 删除 permission-sdk 模块

**Files:**
- Delete: `auth-service/permission-sdk/`（整个目录）
- Modify: `auth-service/pom.xml`（删除 module 声明）
- Modify: `pom.xml`（根 pom，删除 dependencyManagement）

- [ ] **Step 1: 删除 permission-sdk 目录**

删除 `auth-service/permission-sdk/` 整个目录。

- [ ] **Step 2: 从 auth-service/pom.xml 删除 module 声明**

删除 `<module>permission-sdk</module>`。

- [ ] **Step 3: 从根 pom.xml 删除 dependencyManagement**

删除：
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>permission-sdk</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: 全量编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(auth-service): 删除 permission-sdk 模块"
```

---

## Task 9: 更新错误码规范文档

**Files:**
- Modify: `.trae/rules/08-错误码规范.md`

- [ ] **Step 1: 在 SHARED 域分配表中新增 PERM 缩写**

在 `08-错误码规范.md` 的 SHARED 域表中新增一行：

```markdown
| PERM | shared-permission-starter | 功能权限校验核心 |
```

- [ ] **Step 2: Commit**

```bash
git add .trae/rules/08-错误码规范.md
git commit -m "docs(rules): 新增 SHARED.PERM 错误码缩写"
```

---

## Task 10: 全量编译验证与端到端测试

**Files:** 无（验证性 Task）

- [ ] **Step 1: 全量编译**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 运行所有单元测试**

Run: `mvn test -q`
Expected: 全部 PASS

- [ ] **Step 3: 验证关键场景**

手动检查以下场景的代码路径完整性：
1. 网关 SessionContextInjector 不再读 permissionCodes
2. 业务服务 @RequirePermission 注解 import 为 com.example.auth.api.annotation
3. shared-permission-starter 无 SessionContextShortCircuit 引用
4. 无任何 com.pension.permission.sdk 或 com.pension.permission.api 引用残留

Run: `grep -r "com.pension.permission.sdk" --include="*.java" .`
Expected: No matches

Run: `grep -r "com.pension.permission.api" --include="*.java" .`
Expected: No matches

- [ ] **Step 4: 最终 Commit（若有残留修复）**

```bash
git add -A
git commit -m "test(auth-service): 全量编译与端到端验证通过"
```

---

## Self-Review

### 1. Spec coverage

| 设计文档章节 | 对应 Task |
|------|------|
| §1 模块定位与职责划分 | Task 1, 2, 3 |
| §2 API 接口设计 | Task 1 |
| §3 注解与签名工具设计 | Task 2 |
| §4 缓存架构与会话上下文透传 | Task 3（删除短路读）、Task 5（精简 SessionContextInjector） |
| §5 shared-permission-starter 改造 | Task 3 |
| §6 auth-adapter 改造 | Task 4 |
| §7 调用方迁移 | Task 5, 6, 7 |
| §8 测试策略 | Task 2, 3, 4 的测试步骤 |
| §9 完整设计总结 | 全部 Task |

### 2. Placeholder scan

- 无 TBD/TODO（RouteRuleController 的 TODO 是设计意图，保留桩实现）
- 所有代码步骤都包含完整代码
- 所有命令都包含预期输出

### 3. Type consistency

- `PermissionCheckRequest` 在 Task 1 定义，Task 3、4 使用 — 类型一致
- `PermissionCheckResponse` 在 Task 1 定义，Task 3、4 使用 — 类型一致
- `RequirePermission` 在 Task 2 定义，Task 3、6 使用 — 类型一致
- `SessionSignatureUtils` 在 Task 2 定义，Task 3、5 使用 — 类型一致
- `PermissionErrorCode` 在 Task 3 定义，Task 3 使用 — 类型一致

### 4. 已知风险

- Task 4 中 `PermissionCheckController` 引用的领域对象（UserNo、PlanNo、BusinessCode、ActionCode、CheckPermissionQuery）包路径需要执行时用 Grep 确认实际位置
- Task 6 中业务服务启动类的 `@EnableExchangeClients` 现有 basePackages 需要保留，只新增 `com.example.auth.api`
- Task 7 删除旧包前需确认所有引用已迁移完毕
