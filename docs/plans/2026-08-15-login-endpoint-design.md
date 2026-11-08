# Login Endpoint Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add login/logout/session-related HTTP endpoints to auth-service so that users can authenticate via the internet/intranet gateways.

**Architecture:** The domain and application layers already exist (`IdentityResolutionService`, `SessionApplicationService`, `SaTokenLoginTokenService`). The missing pieces are: (1) API-layer DTOs and `@HttpExchange` interface, (2) adapter-layer controller, (3) `AuthenticationProvider` implementations so `CredentialAuthenticator` can actually validate passwords/UKeys, (4) channel-aware token issuance so tokens issued by auth-service are recognized by the gateways' channel-specific `StpLogic` instances.

**Tech Stack:** Spring Boot 3.5, sa-token 1.45, MapStruct 1.6.3, JDK 25 (record types, sealed interfaces, pattern matching)

---

## Critical Design Issue: Token Channel Mismatch

The gateways create channel-specific `StpLogic` instances (`loginType=internet/hq/branch`) with separate Redis key namespaces. The auth-service currently uses the default `StpUtil.login()` (`loginType=login`) — tokens issued this way are **invisible** to the gateways.

**Fix:** `SaTokenLoginTokenService` must use channel-specific `StpLogic` instances matching the gateway's configuration. This requires:
1. A mapping from `AnnuityChannel` → sa-token `loginType` (NETAPP→internet, TELLER→hq, BANK_BRANCH→branch)
2. Registering channel-specific `StpLogic` beans in auth-service infrastructure
3. Updating `SaTokenLoginTokenService.issueToken()` to select the correct `StpLogic` by channel

## AnnuityChannel → loginType Mapping

| AnnuityChannel | loginType | Gateway Token Header | Gateway |
|---|---|---|---|
| `NETAPP` (01, 网上渠道) | `internet` | `satoken-internet` | internet-gateway |
| `TELLER` (02, 总部柜面) | `hq` | `satoken-hq` | intranet-gateway |
| `BANK_BRANCH` (05, 合作银行网点) | `branch` | `satoken-branch` | intranet-gateway |

---

### Task 1: Add SessionApi @HttpExchange Interface

**Files:**
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/SessionApi.java`

**Step 1: Create the SessionApi interface**

```java
package com.example.auth.api;

import com.example.auth.api.command.LoginRequest;
import com.example.auth.api.dto.LoginResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 会话管理 API（登录/登出/选计划/查可选计划）.
 *
 * <p>登录请求通过网关白名单路径到达 auth-service：
 * <ul>
 *   <li>/internet/auth/login — 网上渠道</li>
 *   <li>/hq/auth/login — 总部渠道</li>
 *   <li>/branch/auth/login — 网点渠道</li>
 * </ul>
 */
@HttpExchange("/auth")
public interface SessionApi {

  @PostExchange("/login")
  ApiResult<LoginResponse> login(@RequestBody @Valid LoginRequest request);

  @PostExchange("/logout")
  ApiResult<Void> logout();

  @PostExchange("/select-plan")
  ApiResult<Void> selectPlan(@RequestBody @Valid SelectPlanRequest request);

  @PostExchange("/selectable-plans")
  ApiResult<SelectablePlansResponse> listSelectablePlans();
}
```

Note: `logout`/`selectPlan`/`listSelectablePlans` don't need request body — they derive the session from the token header.

**Step 2: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/SessionApi.java
git commit -m "feat(auth-api): add SessionApi @HttpExchange interface for login/logout/session"
```

---

### Task 2: Add Login/Session Request & Response DTOs

**Files:**
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/LoginRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/SelectPlanRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/LoginResponse.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/SelectablePlansResponse.java`

**Step 1: Create LoginRequest**

```java
package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求.
 *
 * @param credentialType 凭证类型: PASSWORD / U_KEY
 * @param ownerType      凭证持有者类型: USER / CUSTOMER / PLAN
 * @param ownerId        凭证持有者 ID（USER 时为 userNo，CUSTOMER 时为 customerNo，PLAN 时为 planNo）
 * @param channel        登录渠道: NETAPP / TELLER / BANK_BRANCH
 * @param proof          凭证证明（密码明文 / UKey 签名）
 * @param phoneNumber    经办手机号（客户/计划级凭证登录时必填，账号级凭证传 null）
 */
public record LoginRequest(
  @NotBlank String credentialType,
  @NotBlank String ownerType,
  @NotBlank String ownerId,
  @NotBlank String channel,
  @NotBlank String proof,
  String phoneNumber
) {
}
```

**Step 2: Create SelectPlanRequest**

```java
package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 选择计划请求.
 *
 * @param planId 计划编号
 */
public record SelectPlanRequest(
  @NotBlank String planId
) {
}
```

**Step 3: Create LoginResponse**

```java
package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录响应.
 *
 * @param token      登录 token（需写入对应渠道的 token header 传回网关）
 * @param tokenName  token header 名称（satoken-internet / satoken-hq / satoken-branch）
 * @param accountId  账号 ID
 * @param channel    登录渠道
 */
public record LoginResponse(
  @NotBlank String token,
  @NotBlank String tokenName,
  @NotBlank String accountId,
  @NotBlank String channel
) {
}
```

**Step 4: Create SelectablePlansResponse**

```java
package com.example.auth.api.dto;

import java.util.List;

/**
 * 可选计划列表响应.
 *
 * @param plans 可选计划编号列表
 */
public record SelectablePlansResponse(
  List<String> plans
) {
}
```

**Step 5: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/command/LoginRequest.java \
        auth-service/auth-api/src/main/java/com/example/auth/api/command/SelectPlanRequest.java \
        auth-service/auth-api/src/main/java/com/example/auth/api/dto/LoginResponse.java \
        auth-service/auth-api/src/main/java/com/example/auth/api/dto/SelectablePlansResponse.java
git commit -m "feat(auth-api): add login/session request and response DTOs"
```

---

### Task 3: Register Channel-Specific StpLogic in Auth-Service

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/ChannelStpLogicConfig.java`

**Step 1: Create ChannelStpLogicConfig**

This config mirrors what `ChannelAwareSaRouter` does in the gateway, but for the servlet-based auth-service. It registers channel-specific `StpLogic` instances so `SaTokenLoginTokenService` can issue tokens that the gateways will recognize.

```java
package com.pension.permission.infrastructure.channel.config;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 认证服务渠道 StpLogic 注册配置.
 *
 * <p>网关侧通过 {@code ChannelAwareSaRouter} 为每个渠道创建独立的 StpLogic，
 * token 存储在 Redis 中按 loginType 隔离。auth-service 必须使用相同的 loginType
 * 签发 token，否则网关无法识别。
 *
 * <p>渠道 → loginType 映射：
 * <ul>
 *   <li>NETAPP → internet（satoken-internet header）</li>
 *   <li>TELLER → hq（satoken-hq header）</li>
 *   <li>BANK_BRANCH → branch（satoken-branch header）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ChannelStpLogicConfig {

  @Bean
  public Map<AnnuityChannel, StpLogic> channelStpLogicMap() {
    Map<AnnuityChannel, StpLogic> map = new EnumMap<>(AnnuityChannel.class);
    registerChannel(map, AnnuityChannel.NETAPP, "internet", "satoken-internet");
    registerChannel(map, AnnuityChannel.TELLER, "hq", "satoken-hq");
    registerChannel(map, AnnuityChannel.BANK_BRANCH, "branch", "satoken-branch");
    log.info("渠道 StpLogic 注册完成: {}", map.keySet());
    return map;
  }

  private void registerChannel(Map<AnnuityChannel, StpLogic> map, AnnuityChannel channel,
                                String loginType, String tokenName) {
    StpLogic logic = new StpLogic(loginType);
    SaTokenConfig config = new SaTokenConfig();
    config.setTokenName(tokenName);
    config.setIsReadHeader(true);
    config.setIsReadCookie(false);
    logic.setConfig(config);
    map.put(channel, logic);
  }
}
```

**Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/ChannelStpLogicConfig.java
git commit -m "feat(auth-infra): register channel-specific StpLogic beans for token issuance"
```

---

### Task 4: Update SaTokenLoginTokenService to Use Channel-Specific StpLogic

**Files:**
- Modify: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java`

**Step 1: Update SaTokenLoginTokenService**

Replace `StpUtil.login()` / `StpUtil.getTokenValue()` / `StpUtil.getLoginIdByToken()` / `StpUtil.logoutByTokenValue()` / `StpUtil.kickout()` with channel-aware `StpLogic` calls.

```java
package com.pension.permission.infrastructure.channel.spi;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 基于 Sa-Token 的 {@link LoginTokenService} SPI 实现.
 *
 * <p>使用渠道对应的 StpLogic 实例签发/校验 token，确保签发的 token
 * 与网关侧 {@code ChannelAwareSaRouter} 的 StpLogic 使用相同的 loginType，
 * 从而网关能正确识别 token。</p>
 */
@Slf4j
@Component
public class SaTokenLoginTokenService implements LoginTokenService {

  private final Map<AnnuityChannel, StpLogic> channelStpLogicMap;

  public SaTokenLoginTokenService(Map<AnnuityChannel, StpLogic> channelStpLogicMap) {
    this.channelStpLogicMap = channelStpLogicMap;
  }

  @Override
  public String issueToken(UserNo accountId, AnnuityChannel channel) {
    if (accountId == null || channel == null) {
      throw new IllegalArgumentException("accountId 和 channel 不能为空");
    }
    StpLogic stpLogic = resolveStpLogic(channel);
    stpLogic.login(accountId.value());
    String tokenValue = stpLogic.getTokenValue();

    log.debug("签发 token: accountId={}, channel={}, loginType={}", accountId.value(), channel, stpLogic.getLoginType());
    return tokenValue;
  }

  @Override
  public Optional<UserNo> verifyToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    // 遍历所有渠道的 StpLogic 尝试验证（token 可能来自任一渠道）
    for (Map.Entry<AnnuityChannel, StpLogic> entry : channelStpLogicMap.entrySet()) {
      try {
        Object loginId = entry.getValue().getLoginIdByToken(token);
        if (loginId != null) {
          return Optional.of(UserNo.of(loginId.toString()));
        }
      } catch (NotLoginException ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }
    return Optional.empty();
  }

  @Override
  public void invalidateToken(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    for (StpLogic stpLogic : channelStpLogicMap.values()) {
      try {
        stpLogic.logoutByTokenValue(token);
      } catch (Exception ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }
    log.debug("登出 token: tokenLength={}", token.length());
  }

  @Override
  public void invalidateAllTokensOf(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    for (StpLogic stpLogic : channelStpLogicMap.values()) {
      try {
        stpLogic.kickout(accountId.value());
      } catch (Exception ignored) {
        // 该渠道可能无此账号登录态
      }
    }
    log.debug("账号强制下线: accountId={}", accountId.value());
  }

  /**
   * 获取渠道对应的 token header 名称（供 Controller 返回给前端）.
   */
  public String getTokenName(AnnuityChannel channel) {
    StpLogic stpLogic = resolveStpLogic(channel);
    return stpLogic.getConfigOrGlobal().getTokenName();
  }

  private StpLogic resolveStpLogic(AnnuityChannel channel) {
    StpLogic stpLogic = channelStpLogicMap.get(channel);
    if (stpLogic == null) {
      throw new IllegalArgumentException("未注册该渠道的 StpLogic: " + channel);
    }
    return stpLogic;
  }
}
```

**Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java
git commit -m "refactor(auth-infra): use channel-specific StpLogic for token issuance/verification"
```

---

### Task 5: Add PasswordAuthenticationProvider Implementation

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/credential/spi/PasswordAuthenticationProvider.java`
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/credential/config/CredentialInfrastructureConfig.java`

**Step 1: Create PasswordAuthenticationProvider**

`CredentialAuthenticator` dispatches to `Map<Class<? extends Credential>, AuthenticationProvider>` — we need at least a password provider.

```java
package com.pension.permission.infrastructure.credential.spi;

import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.user.service.AuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码认证策略 — 校验 BCrypt 哈希.
 */
@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  @Override
  public boolean authenticate(Credential credential, String proof) {
    if (!(credential instanceof PasswordCredential pc)) {
      return false;
    }
    return ENCODER.matches(proof, pc.passwordHash());
  }
}
```

**Step 2: Create CredentialInfrastructureConfig to wire the provider map**

```java
package com.pension.permission.infrastructure.credential.config;

import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.user.service.AuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 凭证域基础设施配置 — 装配 AuthenticationProvider 策略 Map.
 */
@Configuration
public class CredentialInfrastructureConfig {

  @Bean
  public Map<Class<? extends Credential>, AuthenticationProvider> authenticationProviderMap(
    PasswordAuthenticationProvider passwordProvider
  ) {
    return Map.of(PasswordCredential.class, passwordProvider);
  }
}
```

Note: This requires `spring-security-crypto` dependency. Add it to `auth-infrastructure/pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
</dependency>
```

**Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/credential/spi/PasswordAuthenticationProvider.java \
        auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/credential/config/CredentialInfrastructureConfig.java
git commit -m "feat(auth-infra): add PasswordAuthenticationProvider with BCrypt"
```

---

### Task 6: Add SessionController in Auth-Adapter

**Files:**
- Create: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/channel/SessionController.java`

**Step 1: Create SessionController**

This is the HTTP entry point. It converts API DTOs → application-layer CQE objects, delegates to `SessionApplicationService`, and returns `LoginResponse` with the token + tokenName so the client knows which header to use.

```java
package com.example.auth.adapter.channel;

import com.example.auth.api.SessionApi;
import com.example.auth.api.command.LoginRequest;
import com.example.auth.api.command.SelectPlanRequest;
import com.example.auth.api.dto.LoginResponse;
import com.example.auth.api.dto.SelectablePlansResponse;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.channel.LogoutCommand;
import com.pension.permission.application.channel.OpenSessionWithCredentialCommand;
import com.pension.permission.application.channel.SelectPlanCommand;
import com.pension.permission.application.channel.SessionApplicationService;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.infrastructure.channel.spi.SaTokenLoginTokenService;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理 Controller — 登录/登出/选计划/查可选计划.
 */
@RestController
@RequiredArgsConstructor
public class SessionController implements SessionApi {

  private final SessionApplicationService sessionApplicationService;
  private final SaTokenLoginTokenService loginTokenService;

  @Override
  public ApiResult<LoginResponse> login(LoginRequest request) {
    AnnuityChannel channel = AnnuityChannel.valueOf(request.channel());
    CredentialOwner owner = resolveOwner(request.ownerType(), request.ownerId());
    Mobile phone = request.phoneNumber() != null ? new Mobile(request.phoneNumber()) : null;

    OpenSessionWithCredentialCommand command = new OpenSessionWithCredentialCommand(
      owner, channel, request.proof(), phone);

    SessionId sessionId = sessionApplicationService.openSessionWithCredential(command);

    String tokenName = loginTokenService.getTokenName(channel);
    LoginResponse response = new LoginResponse(
      sessionId.value(),
      tokenName,
      extractAccountId(sessionId),
      channel.name()
    );
    return ApiResult.success(response);
  }

  @Override
  public ApiResult<Void> logout() {
    SessionId sessionId = currentSessionId();
    sessionApplicationService.logout(new LogoutCommand(sessionId, currentAccountId()));
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<Void> selectPlan(SelectPlanRequest request) {
    SessionId sessionId = currentSessionId();
    sessionApplicationService.selectPlan(
      new SelectPlanCommand(sessionId, PlanNo.of(request.planId()), currentAccountId()));
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<SelectablePlansResponse> listSelectablePlans() {
    SessionId sessionId = currentSessionId();
    SelectablePlanScope scope = sessionApplicationService.listSelectablePlans(sessionId);
    // 将 SelectablePlanScope 转为简单 planId 列表
    java.util.List<String> plans = switch (scope) {
      case com.pension.permission.domain.channel.valueobject.EnumeratedPlans ep ->
        ep.plans().stream().map(p -> p.value()).toList();
      case com.pension.permission.domain.channel.valueobject.AllPlans ap ->
        java.util.List.of("ALL");
    };
    return ApiResult.success(new SelectablePlansResponse(plans));
  }

  private CredentialOwner resolveOwner(String ownerType, String ownerId) {
    return switch (ownerType) {
      case "USER" -> new UserCredentialOwner(UserNo.of(ownerId));
      case "CUSTOMER" -> new CustomerCredentialOwner(
        com.example.shared.identifier.id.CustomerNo.of(ownerId));
      case "PLAN" -> new PlanCredentialOwner(PlanNo.of(ownerId));
      default -> throw new IllegalArgumentException("未知的凭证持有者类型: " + ownerType);
    };
  }

  /**
   * 从 sa-token 上下文获取当前会话 ID（即 token 值）.
   */
  private SessionId currentSessionId() {
    // 网关注入的 X-Session-Context 或 sa-token 上下文可提供 token
    // 简化实现：从请求头中读取
    String token = cn.dev33.satoken.stp.StpUtil.getTokenValue();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("未登录");
    }
    return new SessionId(token);
  }

  private UserNo currentAccountId() {
    String loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    return UserNo.of(loginId);
  }

  private String extractAccountId(SessionId sessionId) {
    // 从 token 反查 accountId
    return loginTokenService.verifyToken(sessionId.value())
      .map(UserNo::value)
      .orElse("");
  }
}
```

**Step 2: Update SessionApi — add SelectPlanRequest and SelectablePlansResponse imports**

The `SessionApi` interface from Task 1 references `SelectPlanRequest` and `SelectablePlansResponse` — make sure the imports are correct:

```java
import com.example.auth.api.command.SelectPlanRequest;
import com.example.auth.api.dto.SelectablePlansResponse;
```

**Step 3: Commit**

```bash
git add auth-service/auth-adapter/src/main/java/com/example/auth/adapter/channel/SessionController.java
git commit -m "feat(auth-adapter): add SessionController for login/logout/selectPlan"
```

---

### Task 7: Verify Gateway Routing & Whitelist

**Files:**
- Review only (no changes expected)

The gateways already have:
- `internet-gateway`: route `/internet/auth/**` → `lb://auth-service`, whitelist `/internet/auth/login`
- `intranet-gateway`: routes `/hq/auth/**` and `/branch/auth/**` → `lb://auth-service`, whitelists `/hq/auth/login` and `/branch/auth/login`

The `SessionApi` is mapped at `@HttpExchange("/auth")` with `@PostExchange("/login")`, so the full path is `POST /auth/login`.

After gateway routing:
- Internet: `POST /internet/auth/login` → auth-service `POST /auth/login` (path preserved, no StripPrefix)
- HQ: `POST /hq/auth/login` → auth-service `POST /auth/login`
- Branch: `POST /branch/auth/login` → auth-service `POST /auth/login`

**IMPORTANT:** Check whether the gateway routes use `StripPrefix=0` (preserve path) or `StripPrefix=1` (strip one segment). If using `StripPrefix=1`, the auth-service would receive `/auth/login` which is correct. If using `StripPrefix=0`, auth-service would receive `/internet/auth/login` which would need a different `@HttpExchange` path.

From the gateway config analysis: internet-gateway routes `/internet/auth/**` → `lb://auth-service` with path preserved (same as business routes). Need to verify the exact StripPrefix setting.

**Step 1: Check gateway route configuration**

Read gateway `application.yml` route definitions and confirm the path handling.

**Step 2: If StripPrefix needs adjustment, update the route or the @HttpExchange path**

If gateway preserves the full path (e.g., `/internet/auth/login` reaches auth-service as-is), then `@HttpExchange` should be updated:

```java
@HttpExchange // no prefix — each method specifies its full path
public interface SessionApi {
  @PostExchange("/internet/auth/login")
  ApiResult<LoginResponse> loginInternet(@RequestBody @Valid LoginRequest request);

  @PostExchange("/hq/auth/login")
  ApiResult<LoginResponse> loginHq(@RequestBody @Valid LoginRequest request);

  @PostExchange("/branch/auth/login")
  ApiResult<LoginResponse> loginBranch(@RequestBody @Valid LoginRequest request);
}
```

Alternatively, if StripPrefix=1 is used, the path received by auth-service is `/auth/login` and the original `@HttpExchange("/auth")` + `@PostExchange("/login")` works.

**Step 3: Commit any gateway or API changes**

---

### Task 8: Verify Build Compiles

**Step 1: Run build**

```bash
mvn clean install -pl auth-service -am -DskipTests
```

**Step 2: Fix any compilation errors**

Common issues to expect:
- Missing `spring-security-crypto` dependency in `auth-infrastructure/pom.xml`
- `Mobile` class import path may differ
- `CustomerNo` import path may differ
- `SelectablePlanScope` sealed interface pattern matching may need adjustment

**Step 3: Commit fixes**

---

## Summary of Files to Create/Modify

| # | File | Action |
|---|---|---|
| 1 | `auth-api/.../SessionApi.java` | Create |
| 2 | `auth-api/.../command/LoginRequest.java` | Create |
| 3 | `auth-api/.../command/SelectPlanRequest.java` | Create |
| 4 | `auth-api/.../dto/LoginResponse.java` | Create |
| 5 | `auth-api/.../dto/SelectablePlansResponse.java` | Create |
| 6 | `auth-infra/.../channel/config/ChannelStpLogicConfig.java` | Create |
| 7 | `auth-infra/.../channel/spi/SaTokenLoginTokenService.java` | Modify |
| 8 | `auth-infra/.../credential/spi/PasswordAuthenticationProvider.java` | Create |
| 9 | `auth-infra/.../credential/config/CredentialInfrastructureConfig.java` | Create |
| 10 | `auth-infra/pom.xml` | Modify (add spring-security-crypto) |
| 11 | `auth-adapter/.../channel/SessionController.java` | Create |

## What This Plan Does NOT Cover (Future Work)

- **UKeyAuthenticationProvider**: Only password auth is implemented. UKey authentication requires hardware integration.
- **CORS configuration**: Gateway CORS `allowed-headers` already includes `satoken-internet`/`satoken-hq`/`satoken-branch` — but the login response must instruct the frontend to use the correct header name.
- **Change password endpoint**: Not in scope.
- **Password hashing on credential creation**: Assumes existing `passwordHash` in DB is BCrypt.
- **Rate limiting / brute force protection**: Should be added at gateway level.
- **Login audit logging**: Domain events (`SessionCreated`) already provide this.
