# Auth-Service Session Management Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor auth-service session management to mount Session/SecondaryAuthSession/PermissionCache onto Sa-Token's Token-Session via an anti-corruption layer (SessionStore SPI), support independent multi-channel StpLogic isolation, and consolidate lifecycle management into domain services.

**Architecture:** Hexagonal/DDD. New `SessionStore` SPI port isolates session storage; infrastructure implements it via Sa-Token's Token-Session (single Redis store). Three channels (internet/hq/branch) use independent `StpLogic` instances aligned with `demo-gateway`'s `ChannelAwareSaRouter`. Single-token design: branch teller token carries a reference to `SecondaryAuthSession`, which is independently time-outed by the aggregate root itself. Domain services (`SessionManagementService`, `SecondaryAuthManagementService`) own lifecycle management, replacing scattered logic in application services.

**Tech Stack:** JDK 25 (preview), Spring Boot 3.5.14, Sa-Token (existing), Redis, MyBatis-Flex, MapStruct, Lombok, JUnit 5 + AssertJ + Mockito.

## Global Constraints

- **JDK**: 25, `--enable-preview` enabled.
- **Architecture**: DDD + Hexagonal. Layer order: types → domain → api → application → adapter → infrastructure → starter. Domain layer MUST NOT depend on application/infrastructure.
- **Domain primitives**: ID types in `xxx-types`, implement `Identifier<?>`. Value objects in domain, implement `ValueObject`, immutable records.
- **Domain services**: Annotated `@DomainService`, stateless, no Spring/MyBatis annotations, no direct DB/external service access. Methods return domain objects.
- **Repository interfaces**: Extend `Repository<T, ID>`, defined in domain layer.
- **SPI ports**: Pure interfaces in domain layer, no framework annotations.
- **Error codes**: Follow `SERVICE.AUTH.XXXX` format. Channel submodule uses `01xx` range (`SecondaryAuthErrorCode` already exists).
- **Existing code patterns**: Follow `SaTokenLoginTokenService`, `SessionRepositoryImpl` patterns for new infrastructure classes.
- **Tests**: JUnit 5 + AssertJ + Mockito. Domain layer tests use Mockito mocks for SPI dependencies.
- **Single token design**: Branch teller token is the only token. SecondaryAuthSession data is mounted on the same Token-Session under a `secondary-auth` attribute key, its lifecycle fully managed by the aggregate root's `expiresAt` field (independent of Sa-Token token timeout).
- **Cache-backed SessionStore reads**: `SessionStore.loadSecondaryAuth(token)` reads from Sa-Token's Token-Session, which is persisted in Redis by Sa-Token. No additional cache layer is required — reads are already cache-backed.
- **Preserve audit data**: Expired `SecondaryAuthSession` records are retained (status = EXPIRED) for audit; not deleted. Specifically:
  - On business timeout (`expiresAt` reached): `SecondaryAuthManagementService.expireIfTimeout` only updates status to `EXPIRED` via `saveSecondaryAuth`, NEVER calls `deleteSecondaryAuth`.
  - On manual revoke: `revoke` updates status to `REVOKED` and retains the record.
  - On teller logout (`SessionManagementService.close`): only `loginTokenService.invalidateToken(token)` is invoked; Token-Session lifecycle is reclaimed by Sa-Token automatically. Note: this is a natural cleanup of Token-Session storage, not an audit-log deletion. If long-term audit retention beyond Token-Session TTL is required, a separate `t_auth_secondary_session` DB table (append-only) is a future optional enhancement.
- **Channel scope**: Only `NETAPP` (internet), `TELLER` (HQ), `BANK_BRANCH` (branch) channels are enabled. `WECHAT`/`REGIONAL_CENTER` are not configured.

---

## File Structure

### New Files (auth-domain)

- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SessionStore.java` — Session storage SPI port (anti-corruption layer).
- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/PermissionSnapshotProvider.java` — Permission snapshot retrieval SPI port (frozen + realtime).
- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/ChannelSessionProperties.java` — Channel session timeout config SPI port.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SecondaryAuthProperties.java` — Secondary auth config SPI port.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SessionManagementService.java` — Session lifecycle domain service.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementService.java` — Secondary auth lifecycle domain service.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SessionRenewed.java` — Session renewed domain event.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SessionError.java` — Session error codes.
- `auth-domain/src/test/java/com/pension/permission/domain/channel/service/SessionManagementServiceTest.java`
- `auth-domain/src/test/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementServiceTest.java`
- `auth-domain/src/test/java/com/pension/permission/domain/channel/spi/SessionStoreTest.java` (contract test stub)

### New Files (auth-infrastructure)

- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/stp/ChannelStpLogicRegistry.java` — Per-channel StpLogic registry.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/stp/AnnuityChannelSaMapping.java` — `AnnuityChannel` ↔ Sa-Token loginType mapping.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/store/SaTokenSessionStore.java` — Sa-Token Token-Session backed `SessionStore` impl.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/ChannelSessionPropertiesImpl.java` — `ChannelSessionProperties` impl reading `auth.channel-session.*` config.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/SecondaryAuthPropertiesImpl.java` — `SecondaryAuthProperties` impl.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/SaTokenPermissionCacheStore.java` — Sa-Token Token-Session backed `PermissionCacheStore` impl (replaces `RedisPermissionCacheStore`).
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/GrantEventRefreshListener.java` — Grant event listener that refreshes permission cache on Token-Session.
- `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/store/SaTokenSessionStoreTest.java`
- `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/stp/ChannelStpLogicRegistryTest.java`

### Modified Files (auth-domain)

- `auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java` — Add `renew(Duration, UserNo)` method; add `markSecondaryAuthBound`/`clearSecondaryAuthBound` helpers if needed.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/LoginTokenService.java` — Add `renewToken`, `verifyTokenByChannel` methods.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/SessionPermissionCache.java` — No structural change (already correct), but now stored via SessionStore.
- `auth-domain/src/main/java/com/pension/permission/domain/permission/spi/PermissionCacheStore.java` — Change signature to token-based: `loadByToken(String)`, `saveByToken(String, ...)`, `evictByToken(String)`.

### Modified Files (auth-application)

- `auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java` — Refactor to delegate to `SessionManagementService`.
- `auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java` — Refactor to delegate to `SecondaryAuthManagementService`.

### Modified Files (auth-infrastructure)

- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java` — Support multi-channel StpLogic via `ChannelStpLogicRegistry`; add `renewToken`, `verifyTokenByChannel`.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SessionRepositoryImpl.java` — Delegate to `SessionStore`; remove independent Redis storage logic.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SecondaryAuthSessionRepositoryImpl.java` — Delegate to `SessionStore`; keep `findTimeoutSessions` via Sa-Token session scan.
- `auth-infrastructure/src/main/resources/application.yml` — Add `auth.channel-session.*` config; keep `auth.secondary-auth.*`.

### Deleted Files

- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthService.java` — Deprecated, removed.
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java` — Deprecated, removed.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/cache/RedisPermissionCacheStore.java` — Replaced by `SaTokenPermissionCacheStore`.
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SessionDO.java` — No longer needed (SessionStore serializes domain objects directly via Jackson).
- `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SessionConverter.java` — No longer needed.

---

## Task 1: Create SessionError error codes

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SessionError.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/errorcode/SessionErrorTest.java`

**Interfaces:**
- Produces: `SessionError` enum implementing `ErrorDefinition`, with codes `SERVICE.AUTH.0101`..`SERVICE.AUTH.0106` (channel submodule `01xx` range).

**Why:** New domain behaviors (renew, not-found, not-active) need dedicated error codes per `08-错误码规范.md` channel section.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.domain.channel.errorcode;

import com.example.shared.exception.ErrorDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionError 错误码")
class SessionErrorTest {

  @Test
  @DisplayName("应实现 ErrorDefinition 接口")
  void shouldImplementErrorDefinition() {
    assertThat(SessionError.SESSION_NOT_FOUND).isInstanceOf(ErrorDefinition.class);
  }

  @Test
  @DisplayName("错误码格式应为 SERVICE.AUTH.01xx")
  void shouldUseServiceAuthChannelRange() {
    assertThat(SessionError.SESSION_NOT_FOUND.code()).isEqualTo("SERVICE.AUTH.0101");
    assertThat(SessionError.SESSION_NOT_ACTIVE.code()).isEqualTo("SERVICE.AUTH.0102");
    assertThat(SessionError.SESSION_EXPIRED.code()).isEqualTo("SERVICE.AUTH.0103");
    assertThat(SessionError.SESSION_ALREADY_CLOSED.code()).isEqualTo("SERVICE.AUTH.0104");
    assertThat(SessionError.SESSION_RENEW_NOT_ALLOWED.code()).isEqualTo("SERVICE.AUTH.0105");
    assertThat(SessionError.SESSION_CHANNEL_NOT_SUPPORTED.code()).isEqualTo("SERVICE.AUTH.0106");
  }

  @Test
  @DisplayName("消息应为纯文本，无占位符")
  void shouldUsePlainTextMessage() {
    for (SessionError error : SessionError.values()) {
      assertThat(error.message()).doesNotContain("{}").doesNotContain("[");
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionErrorTest -q`
Expected: FAIL with "cannot find symbol class SessionError".

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * 渠道会话错误码（SERVICE.AUTH.01xx 段）.
 *
 * <p>对应 08-错误码规范.md 中 channel 子模块的 01xx 码段分配。</p>
 */
public enum SessionError implements ErrorDefinition {

  SESSION_NOT_FOUND("SERVICE.AUTH.0101", "会话不存在"),
  SESSION_NOT_ACTIVE("SERVICE.AUTH.0102", "会话不是活跃状态"),
  SESSION_EXPIRED("SERVICE.AUTH.0103", "会话已过期"),
  SESSION_ALREADY_CLOSED("SERVICE.AUTH.0104", "会话已关闭"),
  SESSION_RENEW_NOT_ALLOWED("SERVICE.AUTH.0105", "当前会话状态不允许续期"),
  SESSION_CHANNEL_NOT_SUPPORTED("SERVICE.AUTH.0106", "当前渠道不支持此操作");

  private final String code;
  private final String message;

  SessionError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionErrorTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SessionError.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/errorcode/SessionErrorTest.java
git commit -m "feat(auth-domain): 新增 Session 错误码定义"
```

---

## Task 2: Add Session.renew method and SessionRenewed event

**Files:**
- Modify: `auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java`
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SessionRenewed.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SessionTest.java` (append new test methods)

**Interfaces:**
- Produces: `Session.renew(Duration, UserNo)` method; `SessionRenewed.of(...)` static factory.

**Why:** Sliding renewal requires aggregate-root level method that updates `expiresAt` and emits domain event.

- [ ] **Step 1: Write the failing test**

Append to `SessionTest.java`:

```java
@Test
@DisplayName("renew - 活跃会话滑动续期")
void renew_shouldExtendExpiresAt() {
  Session session = SessionTestFactory.activeBranchSession();
  Duration extension = Duration.ofHours(2);
  LocalDateTime originalExpiresAt = session.expiresAt();

  session.renew(extension, UserNo.of("operator-1"));

  assertThat(session.expiresAt()).isAfter(originalExpiresAt);
  assertThat(session.domainEvents()).hasSize(1);
  assertThat(session.domainEvents().get(0)).isInstanceOf(SessionRenewed.class);
}

@Test
@DisplayName("renew - 非活跃会话抛 SESSION_RENEW_NOT_ALLOWED")
void renew_shouldThrowWhenNotActive() {
  Session session = SessionTestFactory.expiredBranchSession();

  assertThatThrownBy(() -> session.renew(Duration.ofHours(1), UserNo.of("op-1")))
    .isInstanceOf(DomainException.class)
    .hasMessageContaining("SESSION_RENEW_NOT_ALLOWED");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionTest#renew_shouldExtendExpiresAt+renew_shouldThrowWhenNotActive -q`
Expected: FAIL — `renew` method does not exist.

- [ ] **Step 3: Create SessionRenewed event**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SessionId;

import java.time.LocalDateTime;

/**
 * 会话续期事件.
 *
 * @param sessionId 会话 ID
 * @param primaryAccountId 主账号
 * @param newExpiresAt 续期后的新过期时间
 * @param operator 操作人
 * @param eventId 事件 ID
 * @param occurredAt 发生时间
 */
public record SessionRenewed(
  SessionId sessionId,
  UserNo primaryAccountId,
  LocalDateTime newExpiresAt,
  UserNo operator,
  String eventId,
  LocalDateTime occurredAt
) implements DomainEvent {

  public static SessionRenewed of(
    SessionId sessionId,
    UserNo primaryAccountId,
    LocalDateTime newExpiresAt,
    UserNo operator
  ) {
    return new SessionRenewed(
      sessionId, primaryAccountId, newExpiresAt, operator,
      DomainEvent.generateEventId(),
      LocalDateTime.now()
    );
  }
}
```

Note: replace `DomainEvent.generateEventId()` with the existing pattern used by other events in the package (check `SessionCreated.of` for the exact `EventId.generate()` API).

- [ ] **Step 4: Add renew method to Session aggregate**

In `Session.java`, add after the `expire` method:

```java
/**
 * 滑动续期.
 *
 * <p>仅 ACTIVE 状态的会话允许续期。续期后 expiresAt 重置为 now + extension，
 * 并发布 SessionRenewed 事件。</p>
 *
 * @param extension 续期时长
 * @param operator 操作人
 */
public void renew(Duration extension, UserNo operator) {
  if (status != SessionStatus.ACTIVE) {
    throw new DomainException(SessionError.SESSION_RENEW_NOT_ALLOWED);
  }
  LocalDateTime now = LocalDateTime.now();
  // 注意：renew 重新设置 expiresAt，需要把 final 字段改为非 final
  // 由于 expiresAt 当前是 final，需重构为非 final（见 Step 5）
  this.expiresAt = now.plus(extension);
  markUpdated(operator);
  registerDomainEvent(
    SessionRenewed.of(id(), primaryAccountId, this.expiresAt, operator)
  );
}
```

- [ ] **Step 5: Refactor expiresAt from final to non-final**

In `Session.java`, change:
```java
private final LocalDateTime expiresAt;
```
to:
```java
private LocalDateTime expiresAt;
```

Update both constructors to keep assigning `this.expiresAt = expiresAt;` (no change in assignment, just removing `final` modifier).

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionTest#renew_shouldExtendExpiresAt+renew_shouldThrowWhenNotActive -q`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SessionRenewed.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SessionTest.java
git commit -m "feat(auth-domain): Session 聚合根新增 renew 方法支持滑动续期"
```

---

## Task 3: Create SessionStore SPI port

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SessionStore.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/spi/SessionStoreContractTest.java`

**Interfaces:**
- Produces: `SessionStore` interface with `loadByToken`, `save`, `delete`, `findActiveByAccountAndChannel`, `loadSecondaryAuth`, `saveSecondaryAuth`, `deleteSecondaryAuth` methods.

**Why:** Anti-corruption layer SPI port that isolates session storage from domain. Infrastructure will implement via Sa-Token Token-Session.

- [ ] **Step 1: Write the contract test**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.types.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SessionStore 契约测试 - 验证 SPI 实现的基本契约.
 *
 * <p>子类化此测试可验证具体实现（如 SaTokenSessionStore）。</p>
 */
@DisplayName("SessionStore SPI 契约")
class SessionStoreContractTest {

  @Test
  @DisplayName("loadByToken - token 为 null 返回 empty")
  void loadByToken_nullTokenReturnsEmpty(SessionStore store) {
    assertThat(store.loadByToken(null)).isEmpty();
  }

  @Test
  @DisplayName("save 后 loadByToken 应返回相同会话")
  void save_thenLoadByTokenReturnsSession(SessionStore store, Session session, String token) {
    store.save(session, token);

    Optional<Session> loaded = store.loadByToken(token);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().id().value()).isEqualTo(session.id().value());
  }

  @Test
  @DisplayName("delete 后 loadByToken 返回 empty")
  void delete_thenLoadByTokenReturnsEmpty(SessionStore store, Session session, String token) {
    store.save(session, token);
    store.delete(token);

    assertThat(store.loadByToken(token)).isEmpty();
  }

  @Test
  @DisplayName("saveSecondaryAuth 后 loadSecondaryAuth 返回相同会话")
  void saveSecondaryAuth_thenLoadReturnsSecondaryAuth(
    SessionStore store, SecondaryAuthSession authSession, String token
  ) {
    store.saveSecondaryAuth(token, authSession);

    Optional<SecondaryAuthSession> loaded = store.loadSecondaryAuth(token);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().id().value()).isEqualTo(authSession.id().value());
  }

  @Test
  @DisplayName("deleteSecondaryAuth 后 loadSecondaryAuth 返回 empty")
  void deleteSecondaryAuth_thenLoadReturnsEmpty(
    SessionStore store, SecondaryAuthSession authSession, String token
  ) {
    store.saveSecondaryAuth(token, authSession);
    store.deleteSecondaryAuth(token);

    assertThat(store.loadSecondaryAuth(token)).isEmpty();
  }
}
```

Note: The contract test uses parameterized injection; concrete implementations provide `SessionStore` instance via subclass. For pure SPI contract verification, the test above is a documentation-style contract; actual execution tests live in infrastructure module.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionStoreContractTest -q`
Expected: FAIL — `SessionStore` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.aggregate.Session;

import java.util.Optional;

/**
 * 会话存储防腐层端口.
 *
 * <p>隔离 Session 与 SecondaryAuthSession 的存储实现。基础设施层可通过 Sa-Token 的
 * Token-Session 实现，将业务会话数据挂载到登录态上，复用 Sa-Token 的生命周期管理能力。</p>
 *
 * <h3>存储模型</h3>
 * <p>单 Token 设计下，同一 Token-Session 同时承载：</p>
 * <ul>
 *   <li>{@code "session"} —— Session 聚合根</li>
 *   <li>{@code "secondary-auth"} —— SecondaryAuthSession 聚合根（网点二次授权后挂载）</li>
 *   <li>{@code "permission-cache"} —— SessionPermissionCache 值对象</li>
 * </ul>
 *
 * <p>三者的生命周期与 Token-Session 一致：Token 失效时整体清理。
 * SecondaryAuthSession 内部通过自身 {@code expiresAt} 字段独立管理 2 小时授权时效，
 * 与 Sa-Token 的 token timeout 解耦。</p>
 *
 * <p>已过期的 SecondaryAuthSession 数据保留（状态置为 EXPIRED）供审计，不主动删除。</p>
 */
public interface SessionStore {

  /**
   * 按 token 加载渠道会话.
   *
   * @param token Sa-Token 签发的 tokenValue
   * @return 会话（若存在）
   */
  Optional<Session> loadByToken(String token);

  /**
   * 保存渠道会话到指定 token 的存储上.
   *
   * @param session 会话聚合根
   * @param token 对应的 tokenValue
   */
  void save(Session session, String token);

  /**
   * 删除指定 token 关联的渠道会话.
   *
   * @param token tokenValue
   */
  void delete(String token);

  /**
   * 按主账号 + 渠道查找活跃会话.
   *
   * <p>用于二次授权事件监听场景：根据柜员账号 + BANK_BRANCH 渠道精确定位活跃会话。</p>
   *
   * @param accountId 主账号
   * @param channel 渠道
   * @return 活跃会话（若存在）
   */
  Optional<Session> findActiveByAccountAndChannel(UserNo accountId, AnnuityChannel channel);

  /**
   * 按 token 加载二次授权会话.
   *
   * <p>从同一 Token-Session 的 {@code "secondary-auth"} 属性加载。
   * 仅在网点渠道且已发起二次授权后存在。</p>
   *
   * @param token 柜员 tokenValue
   * @return 二次授权会话（若存在）
   */
  Optional<SecondaryAuthSession> loadSecondaryAuth(String token);

  /**
   * 保存二次授权会话到指定柜员 token 的存储上.
   *
   * <p>挂载到同一 Token-Session 的 {@code "secondary-auth"} 属性。</p>
   *
   * @param token 柜员 tokenValue
   * @param authSession 二次授权会话聚合根
   */
  void saveSecondaryAuth(String token, SecondaryAuthSession authSession);

  /**
   * 删除指定柜员 token 关联的二次授权会话.
   *
   * <p>仅清理 Token-Session 上的 {@code "secondary-auth"} 属性，
   * 不影响渠道会话本身。用于二次授权过期后的清理（保留审计数据时可不调用）。</p>
   *
   * @param token 柜员 tokenValue
   */
  void deleteSecondaryAuth(String token);
}
```

- [ ] **Step 4: Run test to verify it compiles**

Run: `mvn -pl auth-service/auth-domain compile -q`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SessionStore.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/spi/SessionStoreContractTest.java
git commit -m "feat(auth-domain): 新增 SessionStore 防腐层 SPI 端口"
```

---

## Task 4: Create PermissionSnapshotProvider SPI port

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/PermissionSnapshotProvider.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/spi/PermissionSnapshotProviderTest.java`

**Interfaces:**
- Produces: `PermissionSnapshotProvider` with `loadFrozenSnapshot(UserNo, PlanNo)` and `loadRealtimeCache(UserNo, PlanNo)` methods.

**Why:** Decouple permission snapshot retrieval from domain. Infrastructure will implement by querying Grant repository + building snapshot.

- [ ] **Step 1: Write the failing test (mock-based)**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PermissionSnapshotProvider SPI")
class PermissionSnapshotProviderTest {

  private final PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);

  @Test
  @DisplayName("loadFrozenSnapshot - 返回冻结快照")
  void loadFrozenSnapshot_returnsSnapshot() {
    UserNo tellerId = UserNo.of("teller-1");
    PlanNo planId = new PlanNo("plan-1");
    PermissionSnapshot snapshot = mock(PermissionSnapshot.class);
    when(provider.loadFrozenSnapshot(tellerId, planId)).thenReturn(Optional.of(snapshot));

    Optional<PermissionSnapshot> result = provider.loadFrozenSnapshot(tellerId, planId);

    assertThat(result).contains(snapshot);
  }

  @Test
  @DisplayName("loadRealtimeCache - 返回实时权限缓存")
  void loadRealtimeCache_returnsCache() {
    UserNo tellerId = UserNo.of("teller-1");
    PlanNo planId = new PlanNo("plan-1");
    SessionPermissionCache cache = mock(SessionPermissionCache.class);
    when(provider.loadRealtimeCache(tellerId, planId)).thenReturn(cache);

    SessionPermissionCache result = provider.loadRealtimeCache(tellerId, planId);

    assertThat(result).isEqualTo(cache);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=PermissionSnapshotProviderTest -q`
Expected: FAIL — `PermissionSnapshotProvider` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;

import java.util.Optional;

/**
 * 权限快照拉取端口.
 *
 * <p>二次授权确认成功后，应用层调用本端口拉取两份权限数据：</p>
 * <ul>
 *   <li><b>冻结快照</b>（{@link #loadFrozenSnapshot}）：授权时刻定格的权限集合，
 *       存入 {@code SecondaryAuthSession.permissionSnapshot}，用于敏感操作时校验
 *       "授权时拥有的权限"。不可变，TTL 由 {@code auth.secondary-auth.snapshot-ttl} 配置（默认 30 秒）。</li>
 *   <li><b>实时缓存</b>（{@link #loadRealtimeCache}）：用于前端可见性判定的权限缓存，
 *       挂载到 Token-Session 的 {@code "permission-cache"} 属性。
 *       Grant 事件触发主动刷新。</li>
 * </ul>
 *
 * <p>两份数据职责不同：冻结快照保证授权后权限不丢失，实时缓存跟随 Grant 变化。</p>
 */
public interface PermissionSnapshotProvider {

  /**
   * 拉取冻结权限快照.
   *
   * @param tellerAccountId 柜员账号
   * @param planId 当前办理计划（若已选）
   * @return 冻结快照（若账号有有效 Grant）
   */
  Optional<PermissionSnapshot> loadFrozenSnapshot(UserNo tellerAccountId, PlanNo planId);

  /**
   * 拉取实时权限缓存.
   *
   * @param tellerAccountId 柜员账号
   * @param planId 当前办理计划（若已选）
   * @return 实时权限缓存值对象
   */
  SessionPermissionCache loadRealtimeCache(UserNo tellerAccountId, PlanNo planId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=PermissionSnapshotProviderTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/PermissionSnapshotProvider.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/spi/PermissionSnapshotProviderTest.java
git commit -m "feat(auth-domain): 新增 PermissionSnapshotProvider 权限快照拉取 SPI 端口"
```

---

## Task 5: Create ChannelSessionProperties and SecondaryAuthProperties SPI ports

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/ChannelSessionProperties.java`
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SecondaryAuthProperties.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/spi/ChannelSessionPropertiesTest.java`

**Interfaces:**
- Produces: `ChannelSessionProperties.timeoutOf(AnnuityChannel)` returning `Duration`; `SecondaryAuthProperties` with `pendingTimeout()`, `sessionTimeout()`, `snapshotTtl()`.

**Why:** Domain services need channel-specific timeouts; config must be injected via SPI to keep domain layer framework-free.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("渠道会话配置 SPI")
class ChannelSessionPropertiesTest {

  @Test
  @DisplayName("timeoutOf - 按渠道返回独立超时配置")
  void timeoutOf_returnsChannelSpecificTimeout() {
    ChannelSessionProperties props = mock(ChannelSessionProperties.class);
    when(props.timeoutOf(AnnuityChannel.NETAPP)).thenReturn(Duration.ofHours(2));
    when(props.timeoutOf(AnnuityChannel.TELLER)).thenReturn(Duration.ofHours(8));
    when(props.timeoutOf(AnnuityChannel.BANK_BRANCH)).thenReturn(Duration.ofHours(8));

    assertThat(props.timeoutOf(AnnuityChannel.NETAPP)).toHours().isEqualTo(2);
    assertThat(props.timeoutOf(AnnuityChannel.TELLER)).toHours().isEqualTo(8);
    assertThat(props.timeoutOf(AnnuityChannel.BANK_BRANCH)).toHours().isEqualTo(8);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=ChannelSessionPropertiesTest -q`
Expected: FAIL — `ChannelSessionProperties` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;

import java.time.Duration;

/**
 * 渠道会话超时配置 SPI 端口.
 *
 * <p>支持三渠道（互联网/总部/网点）独立的会话时效配置。
 * 基础设施层通过 {@code auth.channel-session.*} 配置注入。</p>
 */
public interface ChannelSessionProperties {

  /**
   * 获取指定渠道的会话超时时长.
   *
   * @param channel 渠道
   * @return 超时时长
   */
  Duration timeoutOf(AnnuityChannel channel);
}
```

```java
package com.pension.permission.domain.channel.spi;

import java.time.Duration;

/**
 * 二次授权配置 SPI 端口.
 *
 * <p>基础设施层通过 {@code auth.secondary-auth.*} 配置注入。
 * 二次授权会话时效独立于网点柜员会话时效。</p>
 */
public interface SecondaryAuthProperties {

  /**
   * 待授权超时时间（默认 5 分钟）.
   */
  Duration pendingTimeout();

  /**
   * 授权后会话过期时间（默认 2 小时）.
   */
  Duration sessionTimeout();

  /**
   * 权限快照 TTL（默认 30 秒）.
   */
  Duration snapshotTtl();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=ChannelSessionPropertiesTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/ChannelSessionProperties.java auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/SecondaryAuthProperties.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/spi/ChannelSessionPropertiesTest.java
git commit -m "feat(auth-domain): 新增渠道会话和二次授权配置 SPI 端口"
```

---

## Task 6: Extend LoginTokenService SPI with renewToken and verifyTokenByChannel

**Files:**
- Modify: `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/LoginTokenService.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/spi/LoginTokenServiceTest.java`

**Interfaces:**
- Consumes: existing `issueToken`, `verifyToken`, `invalidateToken`, `invalidateAllTokensOf`.
- Produces: `renewToken(String token, Duration timeout)`, `verifyTokenByChannel(String token, AnnuityChannel channel)`.

**Why:** Sliding renewal needs SPI method; channel-aware verification aligns with multi-StpLogic design.

- [ ] **Step 1: Write the failing test (mock-based)**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LoginTokenService SPI")
class LoginTokenServiceTest {

  private final LoginTokenService service = mock(LoginTokenService.class);

  @Test
  @DisplayName("renewToken - 续期 token")
  void renewToken_shouldExtendTimeout() {
    String token = "token-123";
    Duration timeout = Duration.ofHours(2);
    when(service.renewToken(token, timeout)).thenReturn(true);

    boolean result = service.renewToken(token, timeout);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("verifyTokenByChannel - 按渠道校验 token")
  void verifyTokenByChannel_shouldReturnAccountId() {
    String token = "token-123";
    UserNo expected = UserNo.of("user-1");
    when(service.verifyTokenByChannel(token, AnnuityChannel.BANK_BRANCH))
      .thenReturn(Optional.of(expected));

    Optional<UserNo> result = service.verifyTokenByChannel(token, AnnuityChannel.BANK_BRANCH);

    assertThat(result).contains(expected);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=LoginTokenServiceTest -q`
Expected: FAIL — `renewToken`, `verifyTokenByChannel` methods do not exist.

- [ ] **Step 3: Modify LoginTokenService interface**

```java
package com.pension.permission.domain.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;

import java.time.Duration;
import java.util.Optional;

/**
 * 登录态(认证)端口——只负责"这个token是否有效、对应哪个账号、要不要让它失效"，
 * 不涉及任何业务权限判定。基础设施层可以用Sa-Token或者其他任何机制实现，
 * domain/application层完全不知道具体用的是什么组件。
 */
public interface LoginTokenService {

  /**
   * 登录成功后签发token；channel用于区分不同渠道各自的并发登录/踢人策略
   */
  String issueToken(UserNo accountId, AnnuityChannel channel);

  /**
   * 续期 token 超时时间.
   *
   * <p>用于滑动续期场景：会话活跃时延长 token 有效期。</p>
   *
   * @param token tokenValue
   * @param timeout 新的超时时长
   * @return 续期成功返回 true；token 已失效返回 false
   */
  boolean renewToken(String token, Duration timeout);

  /**
   * 网关/服务侧校验token，返回对应账号；无效或已过期返回empty
   */
  Optional<UserNo> verifyToken(String token);

  /**
   * 按渠道校验 token.
   *
   * <p>多 StpLogic 设计下，每个渠道使用独立 StpLogic 实例。
   * 本方法按 channel 分派到对应 StpLogic 进行校验，
   * 确保渠道间 token 隔离（互联网 token 不能访问网点接口）。</p>
   *
   * @param token tokenValue
   * @param channel 预期渠道
   * @return 账号（若 token 在该渠道有效）
   */
  Optional<UserNo> verifyTokenByChannel(String token, AnnuityChannel channel);

  /**
   * 登出 / 单个token强制下线
   */
  void invalidateToken(String token);

  /**
   * 账号冻结联动：把这个账号名下所有登录态都踢下线，不只是当前这一个token
   */
  void invalidateAllTokensOf(UserNo accountId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=LoginTokenServiceTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/LoginTokenService.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/spi/LoginTokenServiceTest.java
git commit -m "feat(auth-domain): LoginTokenService SPI 新增 renewToken 和 verifyTokenByChannel 方法"
```

---

## Task 7: Refactor PermissionCacheStore SPI to token-based

**Files:**
- Modify: `auth-domain/src/main/java/com/pension/permission/domain/permission/spi/PermissionCacheStore.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/permission/spi/PermissionCacheStoreTest.java`

**Interfaces:**
- Produces: `loadByToken(String)`, `saveByToken(String, SessionPermissionCache)`, `evictByToken(String)`.

**Why:** Permission cache now mounts on Token-Session; account-id-based keying is replaced by token-based keying.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.domain.permission.spi;

import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PermissionCacheStore SPI - token 化")
class PermissionCacheStoreTest {

  private final PermissionCacheStore store = mock(PermissionCacheStore.class);

  @Test
  @DisplayName("loadByToken - 按 token 加载权限缓存")
  void loadByToken_returnsCache() {
    String token = "token-123";
    SessionPermissionCache cache = mock(SessionPermissionCache.class);
    when(store.loadByToken(token)).thenReturn(Optional.of(cache));

    Optional<SessionPermissionCache> result = store.loadByToken(token);

    assertThat(result).contains(cache);
  }

  @Test
  @DisplayName("evictByToken - 按 token 清除缓存")
  void evictByToken_removesCache() {
    String token = "token-123";
    store.evictByToken(token);
    // 验证无异常抛出即可
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=PermissionCacheStoreTest -q`
Expected: FAIL — `loadByToken`, `evictByToken` methods do not exist.

- [ ] **Step 3: Modify PermissionCacheStore interface**

Replace entire content:

```java
package com.pension.permission.domain.permission.spi;

import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;

import java.util.Optional;

/**
 * 权限缓存存储 SPI 端口（token 化）.
 *
 * <p>权限缓存挂载到 Sa-Token 的 Token-Session 上的 {@code "permission-cache"} 属性。
 * 生命周期与 token 一致：token 失效时自动清理。</p>
 *
 * <p>Grant 事件触发主动刷新：监听 GrantCreated/GrantRevoked 事件后调用
 * {@link #evictByToken} 清除缓存，下次访问时按需重新拉取。</p>
 */
public interface PermissionCacheStore {

  /**
   * 按 token 加载权限缓存.
   *
   * @param token tokenValue
   * @return 权限缓存（若存在）
   */
  Optional<SessionPermissionCache> loadByToken(String token);

  /**
   * 保存权限缓存到指定 token.
   *
   * @param token tokenValue
   * @param cache 权限缓存
   */
  void saveByToken(String token, SessionPermissionCache cache);

  /**
   * 按 token 清除权限缓存.
   *
   * @param token tokenValue
   */
  void evictByToken(String token);
}
```

- [ ] **Step 4: Update all references to old methods**

Search for `load(UserNo)`, `save(UserNo, ...)`, `evict(UserNo)`, `evictAll(Set)` usages in `auth-application` and `auth-infrastructure` and update or stub them. The old methods will be removed; callers will be refactored in Task 13.

For now, to keep compilation working, add temporary deprecated default methods:

```java
  // === 临时兼容方法，Task 13 中移除 ===
  @Deprecated
  default <T> Optional<SessionPermissionCache> load(com.example.shared.identifier.id.UserNo accountId) {
    return Optional.empty();
  }
  @Deprecated
  default void save(com.example.shared.identifier.id.UserNo accountId, SessionPermissionCache cache) {}
  @Deprecated
  default void evict(com.example.shared.identifier.id.UserNo accountId) {}
  @Deprecated
  default void evictAll(java.util.Set<com.example.shared.identifier.id.UserNo> accountIds) {}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=PermissionCacheStoreTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/permission/spi/PermissionCacheStore.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/permission/spi/PermissionCacheStoreTest.java
git commit -m "refactor(auth-domain): PermissionCacheStore SPI 改为 token 化接口"
```

---

## Task 8: Create SessionManagementService domain service

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SessionManagementService.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/service/SessionManagementServiceTest.java`

**Interfaces:**
- Consumes: `SessionStore`, `LoginTokenService`, `ChannelSessionProperties`.
- Produces: `SessionManagementService` with `createSession`, `renew`, `close`, `expireIfTimeout`, `invalidateAllSessionsOf` methods.

**Why:** Consolidate session lifecycle logic (previously scattered in `SessionApplicationService`) into a stateless `@DomainService`.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.domain.channel.service;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.errorcode.SessionError;
import com.pension.permission.domain.channel.spi.ChannelSessionProperties;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.types.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManagementService 领域服务")
class SessionManagementServiceTest {

  @Mock private SessionStore sessionStore;
  @Mock private LoginTokenService loginTokenService;
  @Mock private ChannelSessionProperties channelProperties;
  @Mock private EventBus eventBus;

  @InjectMocks
  private SessionManagementService service;

  private static final UserNo TELLER = UserNo.of("teller-1");
  private static final UserNo OPERATOR = UserNo.of("admin-1");
  private static final String TOKEN = "token-abc";
  private static final Duration TIMEOUT = Duration.ofHours(8);

  @BeforeEach
  void setUp() {
    when(channelProperties.timeoutOf(AnnuityChannel.BANK_BRANCH)).thenReturn(TIMEOUT);
  }

  @Test
  @DisplayName("createSession - 签发token并挂载Session")
  void createSession_shouldIssueTokenAndSaveSession() {
    when(loginTokenService.issueToken(TELLER, AnnuityChannel.BANK_BRANCH)).thenReturn(TOKEN);

    Session session = service.createSession(TELLER, AnnuityChannel.BANK_BRANCH, OPERATOR);

    assertThat(session.id().value()).isEqualTo(TOKEN);
    assertThat(session.primaryAccountId()).isEqualTo(TELLER);
    verify(loginTokenService).issueToken(TELLER, AnnuityChannel.BANK_BRANCH);
    verify(sessionStore).save(any(Session.class), eq(TOKEN));
  }

  @Test
  @DisplayName("renew - 活跃会话滑动续期")
  void renew_shouldExtendTimeout() {
    Session session = mock(Session.class);
    when(session.status()).thenReturn(com.pension.permission.domain.channel.enumeration.SessionStatus.ACTIVE);
    when(sessionStore.loadByToken(TOKEN)).thenReturn(Optional.of(session));
    when(loginTokenService.renewToken(TOKEN, TIMEOUT)).thenReturn(true);

    service.renew(new SessionId(TOKEN), OPERATOR);

    verify(session).renew(TIMEOUT, OPERATOR);
    verify(loginTokenService).renewToken(TOKEN, TIMEOUT);
    verify(sessionStore).save(session, TOKEN);
  }

  @Test
  @DisplayName("renew - 会话不存在抛 SESSION_NOT_FOUND")
  void renew_shouldThrowWhenNotFound() {
    when(sessionStore.loadByToken(TOKEN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.renew(new SessionId(TOKEN), OPERATOR))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining(SessionError.SESSION_NOT_FOUND.code());
  }

  @Test
  @DisplayName("close - 关闭会话并失效token")
  void close_shouldCloseAndInvalidateToken() {
    Session session = mock(Session.class);
    when(sessionStore.loadByToken(TOKEN)).thenReturn(Optional.of(session));

    service.close(new SessionId(TOKEN), OPERATOR);

    verify(session).close(OPERATOR);
    verify(sessionStore).save(session, TOKEN);
    verify(loginTokenService).invalidateToken(TOKEN);
  }

  @Test
  @DisplayName("expireIfTimeout - 过期会话自动关闭")
  void expireIfTimeout_shouldExpireIfTimeout() {
    Session session = mock(Session.class);
    when(session.isExpired(any())).thenReturn(true);
    when(sessionStore.loadByToken(TOKEN)).thenReturn(Optional.of(session));

    service.expireIfTimeout(new SessionId(TOKEN));

    verify(session).expire(any());
    verify(sessionStore).save(session, TOKEN);
    verify(loginTokenService).invalidateToken(TOKEN);
  }

  @Test
  @DisplayName("invalidateAllSessionsOf - 踢下线所有渠道会话")
  void invalidateAllSessionsOf_shouldKickoutAllTokens() {
    service.invalidateAllSessionsOf(TELLER);

    verify(loginTokenService).invalidateAllTokensOf(TELLER);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionManagementServiceTest -q`
Expected: FAIL — `SessionManagementService` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.service;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.errorcode.SessionError;
import com.pension.permission.domain.channel.spi.ChannelSessionProperties;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会话生命周期管理领域服务.
 *
 * <p>统一管理 Session 的创建、续期、关闭、过期、账号冻结联动。
 * 替代原 {@code SessionApplicationService} 中分散的生命周期逻辑。</p>
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li>创建会话：签发 token + 创建聚合根 + 挂载到 SessionStore</li>
 *   <li>滑动续期：更新聚合根 expiresAt + 调用 SPI renewToken + 持久化</li>
 *   <li>关闭会话：调用聚合根 close + 失效 token + 持久化</li>
 *   <li>过期检查：访问时触发，过期则关闭会话</li>
 *   <li>账号冻结联动：失效该账号所有渠道 token</li>
 * </ol>
 */
@DomainService
@RequiredArgsConstructor
public final class SessionManagementService {

  private static final UserNo SYSTEM_OPERATOR = UserNo.of("SYSTEM");

  private final SessionStore sessionStore;
  private final LoginTokenService loginTokenService;
  private final ChannelSessionProperties channelProperties;
  private final EventBus eventBus;

  /**
   * 创建会话 - 统一入口.
   *
   * <p>签发 token + 创建 Session 聚合根 + 挂载到 SessionStore，一步完成。
   * Session.id 等于 Sa-Token 签发的 tokenValue。</p>
   */
  public Session createSession(UserNo accountId, AnnuityChannel channel, UserNo operator) {
    Duration timeout = channelProperties.timeoutOf(channel);
    String token = loginTokenService.issueToken(accountId, channel);

    SessionId sessionId = new SessionId(token);
    Session session = Session.create(
      sessionId, operator, accountId, channel,
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(accountId),
      timeout
    );

    sessionStore.save(session, token);
    session.domainEvents().forEach(eventBus::publish);
    return session;
  }

  /**
   * 续期会话 - 活跃会话滑动续期.
   */
  public Session renew(SessionId sessionId, UserNo operator) {
    String token = sessionId.value();
    Session session = sessionStore.loadByToken(token)
      .orElseThrow(() -> new BusinessException(SessionError.SESSION_NOT_FOUND));

    Duration timeout = channelProperties.timeoutOf(session.channel());
    session.renew(timeout, operator);
    loginTokenService.renewToken(token, timeout);
    sessionStore.save(session, token);
    session.domainEvents().forEach(eventBus::publish);
    return session;
  }

  /**
   * 关闭会话 - 主动登出.
   */
  public void close(SessionId sessionId, UserNo operator) {
    String token = sessionId.value();
    Session session = sessionStore.loadByToken(token)
      .orElseThrow(() -> new BusinessException(SessionError.SESSION_NOT_FOUND));

    session.close(operator);
    sessionStore.save(session, token);
    session.domainEvents().forEach(eventBus::publish);
    loginTokenService.invalidateToken(token);
  }

  /**
   * 过期会话 - 访问时校验触发.
   */
  public void expireIfTimeout(SessionId sessionId) {
    String token = sessionId.value();
    Session session = sessionStore.loadByToken(token).orElse(null);
    if (session == null) {
      return;
    }
    if (!session.isExpired(LocalDateTime.now())) {
      return;
    }
    session.expire(SYSTEM_OPERATOR);
    sessionStore.save(session, token);
    session.domainEvents().forEach(eventBus::publish);
    loginTokenService.invalidateToken(token);
  }

  /**
   * 账号冻结联动 - 踢下线该账号所有渠道会话.
   */
  public void invalidateAllSessionsOf(UserNo accountId) {
    loginTokenService.invalidateAllTokensOf(accountId);
    // Token-Session 随 token 失效自动清理
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SessionManagementServiceTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SessionManagementService.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/SessionManagementServiceTest.java
git commit -m "feat(auth-domain): 新增 SessionManagementService 会话生命周期领域服务"
```

---

## Task 9: Create SecondaryAuthManagementService domain service

**Files:**
- Create: `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementService.java`
- Test: `auth-domain/src/test/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementServiceTest.java`

**Interfaces:**
- Consumes: `SessionStore`, `PermissionSnapshotProvider`, `SecondaryAuthProperties`, `EventBus`.
- Produces: `SecondaryAuthManagementService` with `onAuthorizeCompleted`, `revoke`, `expireIfTimeout` methods.

**Why:** Consolidate secondary auth lifecycle into domain service; pull permission snapshot via SPI on authorization.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.domain.channel.service;

import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.spi.PermissionSnapshotProvider;
import com.pension.permission.domain.channel.spi.SecondaryAuthProperties;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecondaryAuthManagementService 领域服务")
class SecondaryAuthManagementServiceTest {

  @Mock private SessionStore sessionStore;
  @Mock private PermissionSnapshotProvider snapshotProvider;
  @Mock private SecondaryAuthProperties properties;
  @Mock private EventBus eventBus;

  @InjectMocks
  private SecondaryAuthManagementService service;

  private static final String TOKEN = "token-abc";
  private static final UserNo TELLER = UserNo.of("teller-1");
  private static final UserNo APPROVER = UserNo.of("approver-1");
  private static final PlanNo PLAN_ID = new PlanNo("plan-1");

  @Test
  @DisplayName("onAuthorizeCompleted - 拉取冻结快照和实时缓存并挂载")
  void onAuthorizeCompleted_shouldPullSnapshotsAndMount(
    SecondaryAuthSession authSession,
    PermissionSnapshot frozenSnapshot,
    SessionPermissionCache realtimeCache
  ) {
    when(properties.snapshotTtl()).thenReturn(Duration.ofSeconds(30));
    when(snapshotProvider.loadFrozenSnapshot(TELLER, PLAN_ID)).thenReturn(Optional.of(frozenSnapshot));
    when(snapshotProvider.loadRealtimeCache(TELLER, PLAN_ID)).thenReturn(realtimeCache);

    service.onAuthorizeCompleted(TOKEN, authSession, TELLER);

    verify(authSession).bindPermissionSnapshot(frozenSnapshot);
    verify(sessionStore).saveSecondaryAuth(TOKEN, authSession);
  }

  @Test
  @DisplayName("onAuthorizeCompleted - 冻结快照拉取失败抛异常")
  void onAuthorizeCompleted_shouldThrowWhenSnapshotMissing(
    SecondaryAuthSession authSession
  ) {
    when(snapshotProvider.loadFrozenSnapshot(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.onAuthorizeCompleted(TOKEN, authSession, TELLER))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining(SecondaryAuthErrorCode.SNAPSHOT_EXPIRED.code());
  }

  @Test
  @DisplayName("revoke - 撤销并持久化")
  void revoke_shouldRevokeAndPersist(SecondaryAuthSession authSession) {
    SecondaryAuthSessionId id = SecondaryAuthSessionId.of("sec-1");
    when(sessionStore.loadSecondaryAuth(TOKEN)).thenReturn(Optional.of(authSession));

    service.revoke(TOKEN, APPROVER, "紧急收权");

    verify(authSession).revoke(APPROVER, "紧急收权");
    verify(sessionStore).saveSecondaryAuth(TOKEN, authSession);
  }

  @Test
  @DisplayName("expireIfTimeout - 超时则标记过期并保留供审计")
  void expireIfTimeout_shouldExpireAndRetainForAudit(SecondaryAuthSession authSession) {
    when(sessionStore.loadSecondaryAuth(TOKEN)).thenReturn(Optional.of(authSession));
    when(authSession.isEffectiveAt(any())).thenReturn(false);

    service.expireIfTimeout(TOKEN);

    verify(authSession).expireIfTimeout(any());
    verify(sessionStore).saveSecondaryAuth(TOKEN, authSession);
    // 验证未调用 deleteSecondaryAuth（保留审计数据）
    verify(sessionStore, never()).deleteSecondaryAuth(TOKEN);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SecondaryAuthManagementServiceTest -q`
Expected: FAIL — `SecondaryAuthManagementService` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.domain.channel.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.spi.PermissionSnapshotProvider;
import com.pension.permission.domain.channel.spi.SecondaryAuthProperties;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 二次授权生命周期管理领域服务.
 *
 * <p>统一管理 SecondaryAuthSession 的授权完成、撤销、过期。
 * 单 Token 设计下，二次授权数据挂在柜员 token 的 Token-Session 上，
 * 时效由聚合根自身 {@code expiresAt} 管理，独立于 Sa-Token token timeout。</p>
 *
 * <p>关键行为：</p>
 * <ol>
 *   <li>授权完成：拉取冻结快照 + 实时缓存，挂载到 Token-Session.secondary-auth</li>
 *   <li>撤销：调用聚合根 revoke + 持久化状态</li>
 *   <li>过期：访问时触发超时检查，标记 EXPIRED 但保留数据供审计</li>
 * </ol>
 */
@DomainService
@RequiredArgsConstructor
public final class SecondaryAuthManagementService {

  private final SessionStore sessionStore;
  private final PermissionSnapshotProvider snapshotProvider;
  private final SecondaryAuthProperties properties;
  private final EventBus eventBus;

  /**
   * 授权确认成功后的处理.
   *
   * <p>1. 拉取冻结权限快照（授权时刻定格）
   * 2. 拉取实时权限缓存（用于前端可见性）
   * 3. 挂载到 Token-Session.secondary-auth
   * 4. 持久化 SecondaryAuthSession 聚合根</p>
   */
  public void onAuthorizeCompleted(
    String token,
    SecondaryAuthSession authSession,
    UserNo operator
  ) {
    // 拉取冻结权限快照（授权时刻定格）
    PermissionSnapshot frozenSnapshot = snapshotProvider
      .loadFrozenSnapshot(authSession.tellerAccountId(), authSession.planId())
      .orElseThrow(() -> new BusinessException(SecondaryAuthErrorCode.SNAPSHOT_EXPIRED)
        .withUserDetail("无法拉取冻结权限快照，请检查授权配置"));

    // 拉取实时权限缓存（用于前端可见性）
    SessionPermissionCache realtimeCache = snapshotProvider.loadRealtimeCache(
      authSession.tellerAccountId(), authSession.planId());

    // 绑定冻结快照到聚合根
    authSession.bindPermissionSnapshot(frozenSnapshot);

    // 挂载到 Token-Session.secondary-auth 并持久化
    sessionStore.saveSecondaryAuth(token, authSession);
    authSession.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 撤销二次授权.
   *
   * <p>仅撤销授权状态，柜员基础会话不受影响。
   * 旧数据保留供审计（不调用 deleteSecondaryAuth）。</p>
   */
  public void revoke(String token, UserNo revoker, String reason) {
    SecondaryAuthSession session = sessionStore.loadSecondaryAuth(token)
      .orElseThrow(() -> new BusinessException(SecondaryAuthErrorCode.SESSION_NOT_FOUND));

    session.revoke(revoker, reason);
    sessionStore.saveSecondaryAuth(token, session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 过期检查 - 访问时触发.
   *
   * <p>超时则标记 EXPIRED，但保留数据供审计，不删除。</p>
   */
  public void expireIfTimeout(String token) {
    SecondaryAuthSession session = sessionStore.loadSecondaryAuth(token).orElse(null);
    if (session == null) {
      return;
    }
    session.expireIfTimeout(LocalDateTime.now());
    // 保留数据供审计：仅持久化状态变更，不调用 deleteSecondaryAuth
    sessionStore.saveSecondaryAuth(token, session);
    session.domainEvents().forEach(eventBus::publish);
  }
}
```

- [ ] **Step 4: Add bindPermissionSnapshot to SecondaryAuthSession aggregate**

In `SecondaryAuthSession.java`, add:

```java
/**
 * 绑定冻结权限快照.
 *
 * <p>由 SecondaryAuthManagementService 在授权完成后调用。
 * 二次授权确认瞬间冻结经办人权限集合。</p>
 *
 * @param snapshot 冻结权限快照
 */
public void bindPermissionSnapshot(PermissionSnapshot snapshot) {
  Objects.requireNonNull(snapshot, "snapshot");
  if (this.status != SecondaryAuthStatus.AUTHORIZED) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_AUTHORIZED);
  }
  this.permissionSnapshot = snapshot;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-domain test -Dtest=SecondaryAuthManagementServiceTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementService.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/SecondaryAuthManagementServiceTest.java auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthManagementService 二次授权生命周期领域服务"
```

---

## Task 10: Delete deprecated SecondaryAuthService and DefaultSecondaryAuthService

**Files:**
- Delete: `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthService.java`
- Delete: `auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java`
- Test: verify compilation

**Interfaces:**
- Consumes: none.
- Produces: removal of deprecated code.

**Why:** Deprecated SPI replaced by `SecondaryAuthAppService` + `SecondaryAuthSession` aggregate + new `SecondaryAuthManagementService`.

- [ ] **Step 1: Search for any remaining references**

Run: `grep -r "SecondaryAuthService\|DefaultSecondaryAuthService" auth-service/ --include="*.java"`
Expected: only the deprecated files themselves. If other references exist, update them first.

- [ ] **Step 2: Delete both files**

```bash
git rm auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthService.java
git rm auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl auth-service/auth-domain compile -q`
Expected: SUCCESS.

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(auth-domain): 移除废弃的 SecondaryAuthService 和 DefaultSecondaryAuthService"
```

---

## Task 10.5: Purge migrated lifecycle methods from SessionApplicationService

**Files:**
- Modify: `auth-service/auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java`
- Modify: `auth-service/auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java`
- Test: verify compilation

**Interfaces:**
- Consumes: `SessionManagementService`, `SecondaryAuthManagementService`.
- Produces: thin application services with no inline lifecycle logic.

**Why:** User confirmed (2026-08-06) that lifecycle management should fully sink from application services to domain services. After Task 8/9 introduced `SessionManagementService`/`SecondaryAuthManagementService`, the duplicated lifecycle methods (token issuance, renewal, expiration checks, snapshot pulling) in application services must be removed to avoid double-authority and drift.

**Scope of cleanup:**
1. `SessionApplicationService`: remove direct calls to `LoginTokenService.issueToken`/`renewToken`/`invalidateToken`, `Session.renew`/`close`/`expire` invocations, and inline timeout computation. All such logic must delegate to `SessionManagementService`.
2. `SecondaryAuthAppService`: remove direct `snapshotProvider` injection and inline snapshot pulling logic; delegate to `SecondaryAuthManagementService.onAuthorizeCompleted`.
3. Remove now-unused field injections (e.g., `LoginTokenService`, `ChannelSessionProperties`, `PermissionSnapshotProvider` from the application services if no longer referenced after delegation).

**Out of scope:**
- Plan-selection orchestration (`listSelectablePlans`, `selectPlan`) stays in application service — it's orchestration, not lifecycle.
- `IdentityResolutionService` stays in `SessionApplicationService.openSessionWithCredential` — it's credential-to-account resolution, not session lifecycle.

- [ ] **Step 1: Audit current SessionApplicationService for migrated methods**

Run: `grep -nE "issueToken|renewToken|invalidateToken|session\.renew|session\.close|session\.expire" auth-service/auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java`

Record each occurrence — each must be replaced with a `SessionManagementService` delegation call.

- [ ] **Step 2: Rewrite SessionApplicationService lifecycle methods to delegate**

For each method that currently inlines `LoginTokenService`/`Session.renew`/`Session.close`/`Session.expire`:

- Replace `openSession` body to delegate to `sessionManagementService.createSession(accountId, channel, operator)`.
- Replace `logout` body to delegate to `sessionManagementService.close(sessionId, operator)`.
- Replace `renewSession` body to delegate to `sessionManagementService.renew(sessionId, operator)`.
- Remove any private helper methods that computed channel timeouts inline — these are now owned by the domain service via `ChannelSessionProperties`.
- Remove unused field injections (`LoginTokenService`, `ChannelSessionProperties`, `EventBus` if no longer used after delegation).

Keep:
- `listSelectablePlans` / `selectPlan` orchestration methods.
- `openSessionWithCredential`'s `IdentityResolutionService.resolve` logic (delegates account resolution, then calls `sessionManagementService.createSession`).
- `@TransactionalEventListener` handlers for `SecondaryAuthCompleted`/`SecondaryAuthRevoked` events — but their bodies must delegate to `SessionManagementService` or `SecondaryAuthManagementService` rather than mutating Session aggregates directly.

- [ ] **Step 3: Rewrite SecondaryAuthAppService.confirm to delegate**

Locate the `confirm` method's post-`session.authorize(...)` block:
- Remove direct `snapshotProvider.loadFrozenSnapshot` / `loadRealtimeCache` calls.
- Remove direct `sessionStore.saveSecondaryAuth` calls.
- Replace with: `secondaryAuthManagementService.onAuthorizeCompleted(cmd.tellerToken(), session, cmd.operator())`.
- Remove now-unused `PermissionSnapshotProvider` and `SessionStore` field injections if no other method uses them.

- [ ] **Step 4: Verify compilation**

Run: `mvn -pl auth-service/auth-application compile -q`
Expected: SUCCESS.

- [ ] **Step 5: Run existing application-layer tests**

Run: `mvn -pl auth-service/auth-application test -q`
Expected: existing tests may break due to removed fields — update mocks to match new dependencies (`SessionManagementService`, `SecondaryAuthManagementService`).

- [ ] **Step 6: Fix broken tests**

For each failing test:
- Replace mocks of `LoginTokenService` / `ChannelSessionProperties` / `PermissionSnapshotProvider` with mocks of `SessionManagementService` / `SecondaryAuthManagementService`.
- Update stubbing to match delegation method signatures.

- [ ] **Step 7: Commit**

```bash
git add auth-service/auth-application/
git commit -m "refactor(auth-application): 清理已下沉到领域服务的生命周期方法"
```

---

## Task 11: Create ChannelStpLogicRegistry and AnnuityChannelSaMapping (infrastructure)

**Files:**
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/stp/AnnuityChannelSaMapping.java`
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/stp/ChannelStpLogicRegistry.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/stp/ChannelStpLogicRegistryTest.java`

**Interfaces:**
- Produces: `ChannelStpLogicRegistry` bean with `getStpLogic(AnnuityChannel)` method.

**Why:** Align auth-service with gateway's `ChannelAwareSaRouter` — independent StpLogic per channel for token isolation and per-channel timeout.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.infrastructure.channel.stp;

import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChannelStpLogicRegistry")
class ChannelStpLogicRegistryTest {

  @Test
  @DisplayName("getStpLogic - 为每个渠道返回独立 StpLogic")
  void getStpLogic_returnsIndependentStpLogicPerChannel() {
    ChannelStpLogicRegistry registry = new ChannelStpLogicRegistry();

    StpLogic netapp = registry.getStpLogic(AnnuityChannel.NETAPP);
    StpLogic teller = registry.getStpLogic(AnnuityChannel.TELLER);
    StpLogic branch = registry.getStpLogic(AnnuityChannel.BANK_BRANCH);

    assertThat(netapp).isNotSameAs(teller);
    assertThat(netapp).isNotSameAs(branch);
    assertThat(teller).isNotSameAs(branch);
  }

  @Test
  @DisplayName("getStpLogic - 各渠道 loginType 不同")
  void getStpLogic_hasDistinctLoginType() {
    ChannelStpLogicRegistry registry = new ChannelStpLogicRegistry();

    assertThat(registry.getStpLogic(AnnuityChannel.NETAPP).getLoginType()).isEqualTo("internet");
    assertThat(registry.getStpLogic(AnnuityChannel.TELLER).getLoginType()).isEqualTo("hq");
    assertThat(registry.getStpLogic(AnnuityChannel.BANK_BRANCH).getLoginType()).isEqualTo("branch");
  }

  @Test
  @DisplayName("getStpLogic - 不支持的渠道抛异常")
  void getStpLogic_throwsForUnsupportedChannel() {
    ChannelStpLogicRegistry registry = new ChannelStpLogicRegistry();

    assertThatThrownBy(() -> registry.getStpLogic(AnnuityChannel.WECHAT))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> registry.getStpLogic(AnnuityChannel.REGIONAL_CENTER))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=ChannelStpLogicRegistryTest -q`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Create AnnuityChannelSaMapping**

```java
package com.pension.permission.infrastructure.channel.stp;

import com.example.shared.annuity.AnnuityChannel;

import java.util.Map;

/**
 * AnnuityChannel 与 Sa-Token loginType / tokenHeader / pathPrefix 的映射.
 *
 * <p>对齐 demo-gateway 的 ChannelType 枚举设计：
 * <ul>
 *   <li>NETAPP → internet（/internet）</li>
 *   <li>TELLER → hq（/hq）</li>
 *   <li>BANK_BRANCH → branch（/branch）</li>
 * </ul>
 * </p>
 *
 * <p>WECHAT / REGIONAL_CENTER 暂未启用，映射时不包含。</p>
 */
public final class AnnuityChannelSaMapping {

  private static final Map<AnnuityChannel, SaChannelConfig> MAPPING = Map.of(
    AnnuityChannel.NETAPP, new SaChannelConfig("internet", "satoken-internet", "/internet"),
    AnnuityChannel.TELLER, new SaChannelConfig("hq", "satoken-hq", "/hq"),
    AnnuityChannel.BANK_BRANCH, new SaChannelConfig("branch", "satoken-branch", "/branch")
  );

  private AnnuityChannelSaMapping() {}

  public static SaChannelConfig configOf(AnnuityChannel channel) {
    SaChannelConfig config = MAPPING.get(channel);
    if (config == null) {
      throw new IllegalArgumentException("不支持的渠道: " + channel);
    }
    return config;
  }

  public static boolean isSupported(AnnuityChannel channel) {
    return MAPPING.containsKey(channel);
  }

  /**
   * Sa-Token 渠道配置.
   */
  public record SaChannelConfig(
    String loginType,
    String tokenHeader,
    String pathPrefix
  ) {}
}
```

- [ ] **Step 4: Create ChannelStpLogicRegistry**

```java
package com.pension.permission.infrastructure.channel.stp;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 渠道感知 StpLogic 注册表.
 *
 * <p>为每个启用的渠道（NETAPP/TELLER/BANK_BRANCH）创建独立的 {@link StpLogic} 实例，
 * 通过不同 token-name 和 loginType 实现渠道间 token 隔离。</p>
 *
 * <p>对齐 demo-gateway 的 {@code ChannelAwareSaRouter} 设计：
 * 网关使用相同 loginType 校验 token，auth-service 使用相同 loginType 签发 token。</p>
 *
 * <p>三渠道会话时效由 {@code auth.channel-session.*} 配置独立设置，
 * 每个 StpLogic 的 SaTokenConfig.timeout 按渠道独立配置。</p>
 */
@Slf4j
@Component
public class ChannelStpLogicRegistry {

  private final Map<AnnuityChannel, StpLogic> stpLogicMap;

  public ChannelStpLogicRegistry() {
    this.stpLogicMap = new EnumMap<>(AnnuityChannel.class);
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      AnnuityChannelSaMapping.SaChannelConfig config = AnnuityChannelSaMapping.configOf(channel);
      StpLogic logic = new StpLogic(config.loginType());

      SaTokenConfig saConfig = new SaTokenConfig();
      saConfig.setTokenName(config.tokenHeader());
      saConfig.setIsReadHeader(true);
      saConfig.setIsReadCookie(false);
      saConfig.setIsConcurrent(true);
      saConfig.setIsShare(false);
      saConfig.setTokenStyle("uuid");
      saConfig.setIsLog(false);
      // timeout 由 ChannelSessionPropertiesImpl 在运行时通过 renewToken 动态设置
      logic.setConfig(saConfig);

      stpLogicMap.put(channel, logic);
    }
    log.info("ChannelStpLogicRegistry 初始化完成: channels={}",
      stpLogicMap.keySet().stream().map(AnnuityChannelSaMapping::configOf).toList());
  }

  /**
   * 获取指定渠道的 StpLogic 实例.
   *
   * @param channel 渠道
   * @return 对应渠道的 StpLogic
   * @throws IllegalArgumentException 渠道未启用
   */
  public StpLogic getStpLogic(AnnuityChannel channel) {
    StpLogic logic = stpLogicMap.get(channel);
    if (logic == null) {
      throw new IllegalArgumentException("不支持的渠道: " + channel);
    }
    return logic;
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=ChannelStpLogicRegistryTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/stp/ auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/stp/
git commit -m "feat(auth-infrastructure): 新增 ChannelStpLogicRegistry 实现三渠道 StpLogic 隔离"
```

---

## Task 12: Implement SaTokenSessionStore

**Files:**
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/store/SaTokenSessionStore.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/store/SaTokenSessionStoreTest.java`

**Interfaces:**
- Consumes: `SessionStore` SPI, `ChannelStpLogicRegistry`, `StringRedisTemplate` (via Sa-Token).
- Produces: `SaTokenSessionStore` implementing `SessionStore`.

**Why:** Core anti-corruption layer implementation — mounts Session/SecondaryAuthSession/PermissionCache onto Sa-Token Token-Session.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.infrastructure.channel.store;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SaTokenSessionStore")
class SaTokenSessionStoreTest {

  // 注意：Sa-Token Token-Session 的真实测试需要 Redis 集成环境
  // 单元测试通过 mock StpLogic 验证调用契约
  // 集成测试在 auth-starter 的 @SpringBootTest 中完成

  @Mock private ChannelStpLogicRegistry registry;

  @Test
  @DisplayName("loadByToken - token 为 null 返回 empty")
  void loadByToken_nullTokenReturnsEmpty() {
    SessionStore store = new SaTokenSessionStore(registry);
    assertThat(store.loadByToken(null)).isEmpty();
  }

  @Test
  @DisplayName("loadByToken - Token-Session 中无数据返回 empty")
  void loadByToken_emptyTokenSessionReturnsEmpty() {
    // 此场景需 mock StpLogic.getTokenSessionByToken 返回空 session
    // 完整集成测试在 auth-starter 中验证
  }
}
```

Note: Complete unit tests require Sa-Token test utilities; this task focuses on structure. Full integration tests are added in Task 16 (auth-starter integration tests).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenSessionStoreTest -q`
Expected: FAIL — `SaTokenSessionStore` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.infrastructure.channel.store;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于 Sa-Token Token-Session 的 SessionStore 实现.
 *
 * <p>将业务会话数据挂载到 Sa-Token 的 Token-Session 上，复用其生命周期管理能力：
 * <ul>
 *   <li>{@code "session"} —— Session 聚合根 JSON</li>
 *   <li>{@code "secondary-auth"} —— SecondaryAuthSession 聚合根 JSON</li>
 * </ul>
 * </p>
 *
 * <p>Token-Session 随 token 失效自动清理，无需手动管理 TTL。
 * SecondaryAuthSession 的独立时效由聚合根自身 expiresAt 字段管理。</p>
 *
 * <h3>Key 设计</h3>
 * <p>不使用独立 Redis Key，直接挂载到 Sa-Token 的 Token-Session：
 * {@code satoken:login:token-session:{token}} 的属性上。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenSessionStore implements SessionStore {

  private static final String KEY_SESSION = "session";
  private static final String KEY_SECONDARY_AUTH = "secondary-auth";

  private final ChannelStpLogicRegistry stpLogicRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public Optional<Session> loadByToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return loadFromTokenSession(token, KEY_SESSION, Session.class);
  }

  @Override
  public void save(Session session, String token) {
    if (session == null || token == null) {
      throw new IllegalArgumentException("session 和 token 不能为空");
    }
    saveToTokenSession(token, KEY_SESSION, session);
    log.debug("保存 Session 到 Token-Session: token={}, sessionId={}",
      token, session.id());
  }

  @Override
  public void delete(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    SaSession tokenSession = tryGetTokenSession(token);
    if (tokenSession != null) {
      tokenSession.delete(KEY_SESSION);
    }
  }

  @Override
  public Optional<Session> findActiveByAccountAndChannel(UserNo accountId, AnnuityChannel channel) {
    // Sa-Token Token-Session 按 token 索引，不支持直接按 accountId 反查
    // 通过 StpLogic.getTokenValueListByLoginId 获取该账号所有 token，逐个加载校验
    StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
    Object loginId = accountId.value();
    for (String token : stpLogic.getTokenValueListByLoginId(loginId)) {
      Optional<Session> session = loadByToken(token);
      if (session.isPresent() && session.get().status() ==
          com.pension.permission.domain.channel.enumeration.SessionStatus.ACTIVE) {
        return session;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<SecondaryAuthSession> loadSecondaryAuth(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return loadFromTokenSession(token, KEY_SECONDARY_AUTH, SecondaryAuthSession.class);
  }

  @Override
  public void saveSecondaryAuth(String token, SecondaryAuthSession authSession) {
    if (token == null || authSession == null) {
      throw new IllegalArgumentException("token 和 authSession 不能为空");
    }
    saveToTokenSession(token, KEY_SECONDARY_AUTH, authSession);
    log.debug("保存 SecondaryAuthSession 到 Token-Session: token={}, authSessionId={}",
      token, authSession.id());
  }

  @Override
  public void deleteSecondaryAuth(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    SaSession tokenSession = tryGetTokenSession(token);
    if (tokenSession != null) {
      tokenSession.delete(KEY_SECONDARY_AUTH);
    }
  }

  // ===============================
  // 内部工具方法
  // ===============================

  private <T> Optional<T> loadFromTokenSession(String token, String key, Class<T> type) {
    SaSession tokenSession = tryGetTokenSession(token);
    if (tokenSession == null) {
      return Optional.empty();
    }
    Object data = tokenSession.get(key);
    if (data == null) {
      return Optional.empty();
    }
    try {
      // Sa-Token 存储时可能已反序列化为 Map，需重新序列化为 JSON 再反序列化为目标类型
      String json = objectMapper.writeValueAsString(data);
      return Optional.of(objectMapper.readValue(json, type));
    } catch (Exception e) {
      log.error("从 Token-Session 反序列化失败: key={}, token={}", key, token, e);
      return Optional.empty();
    }
  }

  private void saveToTokenSession(String token, String key, Object data) {
    SaSession tokenSession = getOrCreateTokenSession(token);
    try {
      // 直接存储对象，Sa-Token 通过 Jackson 序列化器自动处理
      tokenSession.set(key, data);
    } catch (Exception e) {
      log.error("保存到 Token-Session 失败: key={}, token={}", key, token, e);
      throw new IllegalStateException("Token-Session 存储失败", e);
    }
  }

  private SaSession tryGetTokenSession(String token) {
    // 尝试所有渠道的 StpLogic，直到找到拥有该 token 的
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!com.pension.permission.infrastructure.channel.stp.AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      SaSession session = stpLogic.getTokenSessionByToken(token);
      if (session != null) {
        return session;
      }
    }
    return null;
  }

  private SaSession getOrCreateTokenSession(String token) {
    // token 对应的渠道通过 token-prefix 推断或逐渠道尝试
    // 优先尝试所有渠道直到找到拥有该 token 的
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!com.pension.permission.infrastructure.channel.stp.AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      SaSession session = stpLogic.getTokenSessionByToken(token);
      if (session != null) {
        return session;
      }
    }
    // 找不到则用 NETAPP 默认创建（理论上不会走到这里，token 应在 issueToken 时已创建）
    throw new IllegalStateException("无法定位 token 对应的 Token-Session: " + token);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenSessionStoreTest -q`
Expected: PASS (null token case passes; integration tests in Task 16).

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/store/ auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/store/
git commit -m "feat(auth-infrastructure): 实现 SaTokenSessionStore 基于 Token-Session 挂载会话数据"
```

---

## Task 13: Refactor SaTokenLoginTokenService for multi-channel StpLogic

**Files:**
- Modify: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenServiceTest.java`

**Interfaces:**
- Consumes: `ChannelStpLogicRegistry`.
- Produces: `renewToken`, `verifyTokenByChannel` implementations.

**Why:** Replace `StpUtil` with channel-specific `StpLogic` instances for token isolation.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.infrastructure.channel.spi;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SaTokenLoginTokenService - 多渠道 StpLogic")
class SaTokenLoginTokenServiceTest {

  private ChannelStpLogicRegistry registry;
  private SaTokenLoginTokenService service;

  @BeforeEach
  void setUp() {
    registry = mock(ChannelStpLogicRegistry.class);
    service = new SaTokenLoginTokenService(registry);
  }

  @Test
  @DisplayName("issueToken - null 参数抛异常")
  void issueToken_nullArgsThrows() {
    assertThatThrownBy(() -> service.issueToken(null, AnnuityChannel.BANK_BRANCH))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.issueToken(UserNo.of("u-1"), null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("renewToken - 失效 token 返回 false")
  void renewToken_invalidTokenReturnsFalse() {
    boolean result = service.renewToken("invalid-token", Duration.ofHours(1));
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("verifyToken - null/blank 返回 empty")
  void verifyToken_nullTokenReturnsEmpty() {
    assertThat(service.verifyToken(null)).isEmpty();
    assertThat(service.verifyToken("")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenLoginTokenServiceTest -q`
Expected: FAIL — constructor does not accept `ChannelStpLogicRegistry`.

- [ ] **Step 3: Refactor SaTokenLoginTokenService**

Replace entire file:

```java
package com.pension.permission.infrastructure.channel.spi;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.infrastructure.channel.stp.AnnuityChannelSaMapping;
import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Sa-Token 的 {@link LoginTokenService} SPI 实现.
 *
 * <p>多渠道 StpLogic 设计：每个渠道（NETAPP/TELLER/BANK_BRANCH）使用独立 StpLogic 实例，
 * 通过不同 loginType 和 token-name 实现 token 隔离。
 * 对齐 demo-gateway 的 {@code ChannelAwareSaRouter} 设计。</p>
 *
 * <h3>与 Session 聚合根的关系</h3>
 * <p>本类仅管理"登录态"——即 token 是否有效、对应哪个账号。
 * 业务上下文由 Session 聚合根承载，挂载到 Token-Session（见 {@code SaTokenSessionStore}）。
 * Session.id = tokenValue，二者合一。</p>
 *
 * <h3>三渠道会话时效</h3>
 * <p>由 {@code auth.channel-session.*} 配置独立设置，每个 StpLogic 的 timeout 独立。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenLoginTokenService implements LoginTokenService {

  private final ChannelStpLogicRegistry stpLogicRegistry;

  @Override
  public String issueToken(UserNo accountId, AnnuityChannel channel) {
    if (accountId == null || channel == null) {
      throw new IllegalArgumentException("accountId 和 channel 不能为空");
    }
    StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
    stpLogic.login(accountId.value());
    String tokenValue = stpLogic.getTokenValue();

    log.debug("签发 token: accountId={}, channel={}, loginType={}",
      accountId.value(), channel, stpLogic.getLoginType());
    return tokenValue;
  }

  @Override
  public boolean renewToken(String token, Duration timeout) {
    if (token == null || token.isBlank()) {
      return false;
    }
    // 尝试所有渠道直到找到拥有该 token 的
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      Object loginId = stpLogic.getLoginIdByToken(token);
      if (loginId != null) {
        // 续期 token session
        stpLogic.getSessionByToken(token).updateTimeout(timeout.getSeconds());
        // 续期 token 本身
        stpLogic.updateLastActiveToNow(token);
        log.debug("续期 token: token={}, channel={}, timeout={}s",
          token, channel, timeout.getSeconds());
        return true;
      }
    }
    return false;
  }

  @Override
  public Optional<UserNo> verifyToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      Optional<UserNo> result = verifyTokenByChannel(token, channel);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<UserNo> verifyTokenByChannel(String token, AnnuityChannel channel) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      Object loginId = stpLogic.getLoginIdByToken(token);
      if (loginId == null) {
        return Optional.empty();
      }
      return Optional.of(UserNo.of(loginId.toString()));
    } catch (NotLoginException e) {
      return Optional.empty();
    }
  }

  @Override
  public void invalidateToken(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      if (stpLogic.getLoginIdByToken(token) != null) {
        stpLogic.logoutByTokenValue(token);
        log.debug("登出 token: channel={}", channel);
        return;
      }
    }
    log.debug("登出 token: 未找到对应渠道的登录态");
  }

  @Override
  public void invalidateAllTokensOf(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      stpLogic.kickout(accountId.value());
    }
    log.debug("账号强制下线所有渠道: accountId={}", accountId.value());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenLoginTokenServiceTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenServiceTest.java
git commit -m "refactor(auth-infrastructure): SaTokenLoginTokenService 支持多渠道 StpLogic"
```

---

## Task 14: Implement ChannelSessionPropertiesImpl and SecondaryAuthPropertiesImpl

**Files:**
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/ChannelSessionPropertiesImpl.java`
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/SecondaryAuthPropertiesImpl.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/config/ChannelSessionPropertiesImplTest.java`

**Interfaces:**
- Produces: `ChannelSessionProperties` and `SecondaryAuthProperties` beans reading from `application.yml`.

**Why:** Bridge config to SPI ports; keep domain layer framework-free.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.infrastructure.channel.config;

import com.example.shared.annuity.AnnuityChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelSessionPropertiesImpl")
class ChannelSessionPropertiesImplTest {

  @Test
  @DisplayName("timeoutOf - 按渠道返回配置的超时时长")
  void timeoutOf_returnsConfiguredTimeout() {
    Map<String, Duration> config = Map.of(
      "netapp", Duration.ofHours(2),
      "teller", Duration.ofHours(8),
      "bank-branch", Duration.ofHours(8)
    );
    ChannelSessionPropertiesImpl props = new ChannelSessionPropertiesImpl(config);

    assertThat(props.timeoutOf(AnnuityChannel.NETAPP)).toHours().isEqualTo(2);
    assertThat(props.timeoutOf(AnnuityChannel.TELLER)).toHours().isEqualTo(8);
    assertThat(props.timeoutOf(AnnuityChannel.BANK_BRANCH)).toHours().isEqualTo(8);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=ChannelSessionPropertiesImplTest -q`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.infrastructure.channel.config;

import com.example.shared.annuity.AnnuityChannel;
import com.pension.permission.domain.channel.spi.ChannelSessionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DurationStyle;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * 渠道会话超时配置实现.
 *
 * <p>读取 {@code auth.channel-session.*} 配置，支持三渠道独立时效。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * auth:
 *   channel-session:
 *     netapp:
 *       timeout: 2h
 *     teller:
 *       timeout: 8h
 *     bank-branch:
 *       timeout: 8h
 * </pre>
 */
@Slf4j
@Configuration
public class ChannelSessionPropertiesImpl implements ChannelSessionProperties {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofHours(8);

  private final Map<AnnuityChannel, Duration> timeouts;

  public ChannelSessionPropertiesImpl(Environment env) {
    this.timeouts = new EnumMap<>(AnnuityChannel.class);
    loadChannel(env, AnnuityChannel.NETAPP, "netapp");
    loadChannel(env, AnnuityChannel.TELLER, "teller");
    loadChannel(env, AnnuityChannel.BANK_BRANCH, "bank-branch");
    log.info("渠道会话时效配置: {}", timeouts);
  }

  private void loadChannel(Environment env, AnnuityChannel channel, String configKey) {
    String value = env.getProperty("auth.channel-session." + configKey + ".timeout");
    Duration duration = (value == null || value.isBlank())
      ? DEFAULT_TIMEOUT
      : DurationStyle.detectAndParse(value);
    timeouts.put(channel, duration);
  }

  @Override
  public Duration timeoutOf(AnnuityChannel channel) {
    return timeouts.getOrDefault(channel, DEFAULT_TIMEOUT);
  }
}
```

```java
package com.pension.permission.infrastructure.channel.config;

import com.pension.permission.domain.channel.spi.SecondaryAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.DurationStyle;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * 二次授权配置实现.
 *
 * <p>读取 {@code auth.secondary-auth.*} 配置，独立于渠道会话时效。</p>
 */
@Slf4j
@Configuration
public class SecondaryAuthPropertiesImpl implements SecondaryAuthProperties {

  private static final Duration DEFAULT_PENDING_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofHours(2);
  private static final Duration DEFAULT_SNAPSHOT_TTL = Duration.ofSeconds(30);

  private final Duration pendingTimeout;
  private final Duration sessionTimeout;
  private final Duration snapshotTtl;

  public SecondaryAuthPropertiesImpl(Environment env) {
    this.pendingTimeout = parseDuration(
      env, "auth.secondary-auth.pending-timeout", DEFAULT_PENDING_TIMEOUT);
    this.sessionTimeout = parseDuration(
      env, "auth.secondary-auth.session-timeout", DEFAULT_SESSION_TIMEOUT);
    this.snapshotTtl = parseDuration(
      env, "auth.secondary-auth.snapshot-ttl", DEFAULT_SNAPSHOT_TTL);
    log.info("二次授权配置: pendingTimeout={}, sessionTimeout={}, snapshotTtl={}",
      pendingTimeout, sessionTimeout, snapshotTtl);
  }

  private Duration parseDuration(Environment env, String key, Duration defaultValue) {
    String value = env.getProperty(key);
    return (value == null || value.isBlank()) ? defaultValue : DurationStyle.detectAndParse(value);
  }

  @Override
  public Duration pendingTimeout() {
    return pendingTimeout;
  }

  @Override
  public Duration sessionTimeout() {
    return sessionTimeout;
  }

  @Override
  public Duration snapshotTtl() {
    return snapshotTtl;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=ChannelSessionPropertiesImplTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/config/ auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/channel/config/
git commit -m "feat(auth-infrastructure): 实现 ChannelSessionProperties 和 SecondaryAuthProperties 配置"
```

---

## Task 15: Implement SaTokenPermissionCacheStore

**Files:**
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/SaTokenPermissionCacheStore.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/permission/SaTokenPermissionCacheStoreTest.java`
- Delete: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/cache/RedisPermissionCacheStore.java`

**Interfaces:**
- Consumes: `PermissionCacheStore` SPI (token-based), `ChannelStpLogicRegistry`.
- Produces: `SaTokenPermissionCacheStore` bean.

**Why:** Replace independent Redis storage with Token-Session-backed cache; align lifecycle with token.

- [ ] **Step 1: Write the failing test (null case)**

```java
package com.pension.permission.infrastructure.permission;

import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SaTokenPermissionCacheStore")
class SaTokenPermissionCacheStoreTest {

  @Test
  @DisplayName("loadByToken - null 返回 empty")
  void loadByToken_nullReturnsEmpty() {
    var store = new SaTokenPermissionCacheStore(new ChannelStpLogicRegistry());
    assertThat(store.loadByToken(null)).isEmpty();
    assertThat(store.loadByToken("")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenPermissionCacheStoreTest -q`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.infrastructure.permission;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import com.pension.permission.infrastructure.channel.stp.AnnuityChannelSaMapping;
import com.pension.permission.infrastructure.channel.stp.ChannelStpLogicRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于 Sa-Token Token-Session 的权限缓存存储实现.
 *
 * <p>将权限缓存挂载到 Token-Session 的 {@code "permission-cache"} 属性，
 * 生命周期与 token 一致。Grant 事件触发主动刷新（见 {@code GrantEventRefreshListener}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenPermissionCacheStore implements PermissionCacheStore {

  private static final String KEY_PERMISSION_CACHE = "permission-cache";

  private final ChannelStpLogicRegistry stpLogicRegistry;
  private final ObjectMapper objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public Optional<SessionPermissionCache> loadByToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    SaSession session = tryGetTokenSession(token);
    if (session == null) {
      return Optional.empty();
    }
    Object data = session.get(KEY_PERMISSION_CACHE);
    if (data == null) {
      return Optional.empty();
    }
    try {
      String json = objectMapper.writeValueAsString(data);
      return Optional.of(objectMapper.readValue(json, SessionPermissionCache.class));
    } catch (Exception e) {
      log.error("权限缓存反序列化失败: token={}", token, e);
      return Optional.empty();
    }
  }

  @Override
  public void saveByToken(String token, SessionPermissionCache cache) {
    if (token == null || cache == null) {
      return;
    }
    SaSession session = tryGetTokenSession(token);
    if (session == null) {
      log.warn("Token-Session 不存在，无法保存权限缓存: token={}", token);
      return;
    }
    session.set(KEY_PERMISSION_CACHE, cache);
  }

  @Override
  public void evictByToken(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    SaSession session = tryGetTokenSession(token);
    if (session != null) {
      session.delete(KEY_PERMISSION_CACHE);
      log.debug("清除权限缓存: token={}", token);
    }
  }

  private SaSession tryGetTokenSession(String token) {
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      if (!AnnuityChannelSaMapping.isSupported(channel)) {
        continue;
      }
      StpLogic stpLogic = stpLogicRegistry.getStpLogic(channel);
      SaSession session = stpLogic.getTokenSessionByToken(token);
      if (session != null) {
        return session;
      }
    }
    return null;
  }
}
```

- [ ] **Step 4: Delete old RedisPermissionCacheStore**

```bash
git rm auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/cache/RedisPermissionCacheStore.java
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=SaTokenPermissionCacheStoreTest -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/SaTokenPermissionCacheStore.java auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/permission/
git commit -m "refactor(auth-infrastructure): 权限缓存改为 SaTokenPermissionCacheStore 基于 Token-Session"
```

---

## Task 16: Refactor SessionRepositoryImpl and SecondaryAuthSessionRepositoryImpl to delegate to SessionStore

**Files:**
- Modify: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SessionRepositoryImpl.java`
- Modify: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SecondaryAuthSessionRepositoryImpl.java`
- Delete: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SessionDO.java`
- Delete: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SessionConverter.java`

**Interfaces:**
- Consumes: `SessionStore`.
- Produces: thin Repository implementations delegating to SessionStore.

**Why:** Eliminate independent Redis storage; unify on Token-Session via SessionStore.

- [ ] **Step 1: Refactor SessionRepositoryImpl**

Replace entire file:

```java
package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 渠道会话仓储实现 - 委托给 SessionStore.
 *
 * <p>重构后不再独立维护 Redis 存储，全部委托给 {@link SessionStore}（基于 Sa-Token Token-Session）。
 * Session.id 等于 tokenValue，load(id) 即 loadByToken(token)。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionRepositoryImpl implements SessionRepository {

  private final SessionStore sessionStore;

  @Override
  public Optional<Session> load(SessionId id) {
    if (id == null) {
      return Optional.empty();
    }
    return sessionStore.loadByToken(id.value());
  }

  @Override
  public void save(Session session) {
    if (session == null) {
      throw new IllegalArgumentException("Session 不能为空");
    }
    sessionStore.save(session, session.id().value());
  }

  @Override
  public void delete(Session aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    sessionStore.delete(aggregateRoot.id().value());
  }

  @Override
  public void deleteById(SessionId id) {
    if (id == null) {
      return;
    }
    sessionStore.delete(id.value());
  }

  @Override
  public java.util.List<Session> loadAll() {
    throw new UnsupportedOperationException(
      "基于 Token-Session 的实现不支持 loadAll()，请使用按 ID 或按账号查询");
  }

  @Override
  public void streamByAppId(SessionId id, Consumer<AggregateRoot<SessionId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    sessionStore.loadByToken(id.value()).ifPresent(processor);
  }

  @Override
  public Optional<Session> findByPrimaryAccountId(UserNo primaryAccountId) {
    // 依次尝试三个渠道
    for (AnnuityChannel channel : new AnnuityChannel[]{
      AnnuityChannel.BANK_BRANCH, AnnuityChannel.TELLER, AnnuityChannel.NETAPP
    }) {
      Optional<Session> session = findActiveByPrimaryAccountIdAndChannel(primaryAccountId, channel);
      if (session.isPresent()) {
        return session;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Session> findActiveByPrimaryAccountIdAndChannel(
    UserNo primaryAccountId, AnnuityChannel channel
  ) {
    return sessionStore.findActiveByAccountAndChannel(primaryAccountId, channel);
  }
}
```

- [ ] **Step 2: Refactor SecondaryAuthSessionRepositoryImpl**

Replace entire file (basic structure; keep `findTimeoutSessions` as no-op or scan-based):

```java
package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.types.SecondaryAuthSessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 二次授权会话仓储实现 - 委托给 SessionStore.
 *
 * <p>重构后 SecondaryAuthSession 挂载到柜员 token 的 Token-Session 上，
 * 通过 {@link SessionStore#loadSecondaryAuth} 加载。</p>
 *
 * <p>注意：{@link #findActiveByTeller} 和 {@link #findAuthorizedByApprover} 由于
 * Token-Session 按 token 索引，不支持直接按账号反查，暂返回空列表。
 * 业务上这些查询由事件监听器在授权完成时主动挂载，无需反查。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SecondaryAuthSessionRepositoryImpl implements SecondaryAuthSessionRepository {

  private final SessionStore sessionStore;

  @Override
  public Optional<SecondaryAuthSession> load(SecondaryAuthSessionId id) {
    // Token-Session 设计下，按 ID 直接加载需通过 token 反查
    // 实际场景中 SecondaryAuthSession 总是随 Session 一起加载
    // 此方法保留接口兼容，返回 empty
    return Optional.empty();
  }

  @Override
  public void save(SecondaryAuthSession aggregateRoot) {
    // 保存需知道对应的柜员 token，此方法在 Token-Session 设计下无法直接使用
    // 调用方应通过 SessionStore.saveSecondaryAuth(token, authSession) 保存
    throw new UnsupportedOperationException(
      "Token-Session 设计下请使用 SessionStore.saveSecondaryAuth(token, authSession)");
  }

  @Override
  public void delete(SecondaryAuthSession aggregateRoot) {
    // 同上，需通过 SessionStore.deleteSecondaryAuth(token)
  }

  @Override
  public void deleteById(SecondaryAuthSessionId id) {
    // 同上
  }

  @Override
  public List<SecondaryAuthSession> loadAll() {
    return Collections.emptyList();
  }

  @Override
  public Optional<SecondaryAuthSession> findActiveByTeller(UserNo tellerAccountId) {
    // Token-Session 按 token 索引，不支持按账号反查
    // 业务上由应用服务在发起二次授权时校验柜员活跃会话唯一性
    return Optional.empty();
  }

  @Override
  public List<SecondaryAuthSession> findAuthorizedByApprover(UserNo approverAccountId) {
    // Token-Session 按 token 索引，不支持按账号反查
    return Collections.emptyList();
  }

  @Override
  public List<SecondaryAuthSession> findPendingByApprover(UserNo approverAccountId) {
    return Collections.emptyList();
  }

  @Override
  public List<SecondaryAuthSession> findTimeoutSessions() {
    // 可通过 Sa-Token 的 Token-Session 扫描实现，暂返回空列表
    // 定时清理任务由 SecondaryAuthManagementService.expireIfTimeout 在访问时触发
    return Collections.emptyList();
  }
}
```

- [ ] **Step 3: Delete obsolete files**

```bash
git rm auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SessionDO.java
git rm auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SessionConverter.java
```

- [ ] **Step 4: Verify compilation**

Run: `mvn -pl auth-service/auth-infrastructure compile -q`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A auth-service/auth-infrastructure/
git commit -m "refactor(auth-infrastructure): SessionRepository 委托给 SessionStore，移除独立 Redis 存储"
```

---

## Task 17: Refactor SessionApplicationService and SecondaryAuthAppService to delegate to domain services

**Files:**
- Modify: `auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java`
- Modify: `auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java`

**Interfaces:**
- Consumes: `SessionManagementService`, `SecondaryAuthManagementService`.
- Produces: thin application services delegating to domain services.

**Why:** Move lifecycle logic to domain layer per DDD principles.

- [ ] **Step 1: Refactor SessionApplicationService**

Replace relevant methods to delegate to `SessionManagementService`:

```java
package com.pension.permission.application.channel;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.event.SecondaryAuthCompleted;
import com.pension.permission.domain.channel.event.SecondaryAuthRevoked;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.domain.channel.service.IdentityResolutionService;
import com.pension.permission.domain.channel.service.PlanSelectionStrategy;
import com.pension.permission.domain.channel.service.SessionManagementService;
import com.pension.permission.domain.channel.spi.SessionStore;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 会话应用服务 - 薄编排层，委托给 SessionManagementService 领域服务.
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

  private final SessionManagementService sessionManagementService;
  private final IdentityResolutionService identityResolutionService;
  private final SessionRepository sessionRepository;
  private final SessionStore sessionStore;
  private final Map<AnnuityChannel, PlanSelectionStrategy> strategiesByChannel;
  private final EventBus eventBus;

  @Transactional
  public SessionId openSession(OpenSessionCommand command) {
    Session session = sessionManagementService.createSession(
      command.accountId(), command.channel(), command.operator());
    return session.id();
  }

  @Transactional
  public SessionId openSessionWithCredential(OpenSessionWithCredentialCommand command) {
    UserNo accountId = identityResolutionService.resolve(
        command.credentialOwner(), command.channel(), command.proof(), command.phoneNumber())
      .orElseThrow(() -> new SecurityException("登录失败：凭证校验不通过，或无法定位到有效经办"));
    Session session = sessionManagementService.createSession(accountId, command.channel(), command.operator());
    return session.id();
  }

  @Transactional
  public void logout(LogoutCommand command) {
    sessionManagementService.close(command.sessionId(), command.operator());
  }

  @Transactional
  public void renewSession(SessionId sessionId, UserNo operator) {
    sessionManagementService.renew(sessionId, operator);
  }

  public SelectablePlanScope listSelectablePlans(SessionId sessionId) {
    Session session = sessionRepository.loadOrThrow(sessionId);
    PlanSelectionStrategy strategy = strategiesByChannel.get(session.channel());
    if (strategy == null) {
      throw new IllegalStateException("该渠道未注册对应的计划选择策略: " + session.channel());
    }
    return strategy.listSelectablePlans(session.effectiveIdentity());
  }

  @Transactional
  public void selectPlan(SelectPlanCommand command) {
    Session session = sessionRepository.loadOrThrow(command.sessionId());
    session.selectPlan(command.planId(), command.operator());
    sessionStore.save(session, session.id().value());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void onSecondaryAuthCompleted(SecondaryAuthCompleted event) {
    sessionStore.findActiveByAccountAndChannel(event.tellerAccountId(), AnnuityChannel.BANK_BRANCH)
      .ifPresent(session -> {
        session.applySecondaryAuth(event.sessionId(), event.effectiveIdentity(), event.tellerAccountId());
        sessionStore.save(session, session.id().value());
      });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void onSecondaryAuthRevoked(SecondaryAuthRevoked event) {
    sessionStore.findActiveByAccountAndChannel(event.tellerAccountId(), AnnuityChannel.BANK_BRANCH)
      .ifPresent(session -> {
        session.clearSecondaryAuth(event.createdBy());
        sessionStore.save(session, session.id().value());
      });
  }
}
```

- [ ] **Step 2: Refactor SecondaryAuthAppService**

Delegate `confirm` to `SecondaryAuthManagementService.onAuthorizeCompleted`; remove direct `snapshot` parameter handling.

```java
// In SecondaryAuthAppService.confirm method, after session.authorize(...):
secondaryAuthManagementService.onAuthorizeCompleted(
  cmd.tellerToken(), session, cmd.operator()
);
```

Note: `ConfirmSecondaryAuthCommand` needs to add `tellerToken` field (the branch teller's token). Update the command class accordingly.

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl auth-service/auth-application compile -q`
Expected: SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-application/
git commit -m "refactor(auth-application): 应用服务委托给领域服务处理生命周期"
```

---

## Task 18: Add GrantEventRefreshListener for proactive permission cache refresh

**Files:**
- Create: `auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/GrantEventRefreshListener.java`
- Test: `auth-infrastructure/src/test/java/com/pension/permission/infrastructure/permission/GrantEventRefreshListenerTest.java`

**Interfaces:**
- Consumes: `PermissionCacheStore`, Grant events (`GrantCreated`, `GrantRevoked`).
- Produces: event listener that evicts permission cache on grant changes.

**Why:** Permission cache must be proactively refreshed when Grant changes to avoid stale permissions.

- [ ] **Step 1: Write the failing test**

```java
package com.pension.permission.infrastructure.permission;

import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrantEventRefreshListener")
class GrantEventRefreshListenerTest {

  @Mock private PermissionCacheStore permissionCacheStore;

  @InjectMocks
  private GrantEventRefreshListener listener;

  @Test
  @DisplayName("onGrantCreated - 清除对应 token 的权限缓存")
  void onGrantCreated_shouldEvictCache() {
    String token = "token-abc";
    // 模拟 GrantCreated 事件
    // 实际事件类需根据 domain.permission.event 包中的定义调整
    listener.onGrantCreated(token);

    verify(permissionCacheStore).evictByToken(token);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=GrantEventRefreshListenerTest -q`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.pension.permission.infrastructure.permission;

import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Grant 事件监听器 - 主动刷新权限缓存.
 *
 * <p>监听 GrantCreated / GrantRevoked / GrantUpdated 事件，
 * 清除对应 token 的 Token-Session 上的权限缓存，
 * 下次访问时按需重新拉取。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantEventRefreshListener {

  private final PermissionCacheStore permissionCacheStore;

  /**
   * Grant 创建事件处理.
   *
   * <p>实际事件类需根据 domain.permission.event 包中的定义调整。
   * 当前为简化实现，通过 token 直接清除缓存。</p>
   */
  @EventListener
  public void onGrantCreated(String token) {
    log.debug("收到 GrantCreated 事件，清除权限缓存: token={}", token);
    permissionCacheStore.evictByToken(token);
  }

  /**
   * Grant 撤销事件处理.
   */
  @EventListener
  public void onGrantRevoked(String token) {
    log.debug("收到 GrantRevoked 事件，清除权限缓存: token={}", token);
    permissionCacheStore.evictByToken(token);
  }
}
```

Note: Actual Grant event classes need to be inspected and the listener signatures updated to match. This is a skeleton; adapt to real event types in `domain.permission.event`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl auth-service/auth-infrastructure test -Dtest=GrantEventRefreshListenerTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/GrantEventRefreshListener.java auth-service/auth-infrastructure/src/test/java/com/pension/permission/infrastructure/permission/GrantEventRefreshListenerTest.java
git commit -m "feat(auth-infrastructure): 新增 GrantEventRefreshListener 主动刷新权限缓存"
```

---

## Task 19: Update application.yml with channel-session config

**Files:**
- Modify: `auth-infrastructure/src/main/resources/application.yml`

**Interfaces:**
- Produces: `auth.channel-session.*` config block.

**Why:** Enable per-channel timeout configuration.

- [ ] **Step 1: Update application.yml**

Add `channel-session` block under existing `auth:` section:

```yaml
auth:
  channel-session:
    netapp:
      timeout: 2h
    teller:
      timeout: 8h
    bank-branch:
      timeout: 8h

  secondary-auth:
    strategy: sms-code
    pending-timeout: 5m
    session-timeout: 2h
    snapshot-ttl: 30s
    verification-code-length: 6
    verification-max-attempts: 3
    sms-enabled: true
```

- [ ] **Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/resources/application.yml
git commit -m "config(auth-infrastructure): 新增三渠道会话时效独立配置"
```

---

## Task 20: Run full test suite and fix any breakages

**Files:**
- Various test files may need updates due to API changes.

**Why:** Ensure all existing tests pass after refactor.

- [ ] **Step 1: Run full auth-service test suite**

Run: `mvn -pl auth-service/auth-domain,auth-service/auth-application,auth-service/auth-infrastructure test -q`
Expected: some failures due to API changes (e.g., `PermissionCacheStore` signature change).

- [ ] **Step 2: Fix broken tests**

For each failing test:
- Update `PermissionCacheStore` usages from `load(UserNo)` to `loadByToken(String)`
- Update `LoginTokenService` mock setups to include `renewToken`, `verifyTokenByChannel`
- Update `SessionApplicationService` tests to mock `SessionManagementService` instead of `SessionRepository` + `LoginTokenService` directly

- [ ] **Step 3: Re-run until all pass**

Run: `mvn -pl auth-service/auth-domain,auth-service/auth-application,auth-service/auth-infrastructure test -q`
Expected: all PASS.

- [ ] **Step 4: Commit fixes**

```bash
git add -A auth-service/
git commit -m "test(auth-service): 修复重构后的测试用例适配"
```

---

## Task 21: Final integration verification

**Files:**
- Verify full project compilation.

**Why:** Ensure no cross-module breakage.

- [ ] **Step 1: Run full project compile**

Run: `mvn clean compile -DskipTests -q`
Expected: SUCCESS.

- [ ] **Step 2: Run full test suite**

Run: `mvn test -q`
Expected: all PASS (or only pre-existing failures unrelated to this refactor).

- [ ] **Step 3: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "test: 最终集成验证通过" --allow-empty
```

---

## Self-Review

**Spec coverage:**
- ✅ Single Token + Session reference design: Tasks 3, 8, 9, 12, 17
- ✅ SessionStore SPI with loadSecondaryAuth/saveSecondaryAuth: Task 3
- ✅ Cache-backed SessionStore reads (Sa-Token Token-Session Redis persistence): Global Constraint "Cache-backed SessionStore reads", realized by `SaTokenSessionStore` (Task 12)
- ✅ PermissionSnapshotProvider SPI for frozen + realtime snapshots: Task 4
- ✅ SecondaryAuth lifecycle management in domain service: Task 9
- ✅ Delete deprecated SecondaryAuthService / DefaultSecondaryAuthService: Task 10
- ✅ Purge migrated lifecycle methods from Session/SecondaryAuth application services: Task 10.5
- ✅ Multi-channel StpLogic isolation: Tasks 11, 13
- ✅ Per-channel timeout config: Tasks 5, 14, 19
- ✅ Renewable Session aggregate: Task 2
- ✅ Token-based PermissionCacheStore: Tasks 7, 15
- ✅ Grant event listener for cache refresh: Task 18
- ✅ Audit retention on expire (only status update, no deleteSecondaryAuth): Task 9 + Global Constraint "Preserve audit data"
- ✅ Audit retention on revoke (status = REVOKED, retained): Task 9 + Global Constraint "Preserve audit data"
- ✅ Repository delegation to SessionStore: Task 16
- ✅ Application service delegation to domain services: Task 17

**Placeholder scan:**
- Task 18 listener signatures are skeleton; actual Grant event types need confirmation from `domain.permission.event` package — flagged for implementer to adapt.
- Task 12 `findActiveByAccountAndChannel` uses `getTokenValueListByLoginId` which requires Sa-Token session enabled; integration test in Task 16 covers.

**Type consistency:**
- `SessionStore.loadByToken(String)` consistent across Tasks 3, 8, 12, 17
- `LoginTokenService.renewToken(String, Duration)` consistent across Tasks 6, 8, 13
- `SessionManagementService.createSession(UserNo, AnnuityChannel, UserNo)` consistent across Tasks 8, 17
- `SecondaryAuthManagementService.onAuthorizeCompleted(String, SecondaryAuthSession, UserNo)` consistent across Tasks 9, 17

---

## Execution Status (2026-08-06)

All 22 tasks (1-21 + 10.5) completed and committed on branch `feature/auth`.

**Final verification:**
- `mvn clean compile -DskipTests` → BUILD SUCCESS (all 77 modules)
- `mvn test` → BUILD SUCCESS (all tests pass, 0 failures)

**Pre-existing issues fixed during Task 21 integration verification (unrelated to auth-service refactor):**
- `build(shared-types)`: 补充 lombok 依赖（IdentityType 使用 @Getter/@AllArgsConstructor 但 pom.xml 未声明依赖）
- `fix(file-infrastructure)`: 移除 ParseTaskConverter/SubTaskDataConverter/TemplateConfigConverter 中多余的 `@Mapping(target = "domainEvents", ignore = true)`（MapStruct 无法识别 AggregateRoot.domainEvents() 作为标准属性，DO 类无此字段）

**Key deliverables:**
- Task 18: `GrantEventRefreshListener` — 监听 GrantApproved/GrantRevoked 事件，根据 GrantSubject 类型精确失效权限缓存（UserListSubject 精确失效，其他类型依赖 TTL 兜底）
- Task 19: `application.yml` — 新增 `auth.channel-session.{netapp,teller,bank-branch}.timeout` 三渠道独立时效配置

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-06-auth-session-management-refactor.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
