package com.pension.permission.domain.channel.aggregate;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecondaryAuthSession 聚合根测试")
class SecondaryAuthSessionTest {

  private final VerificationCodeHasher acceptHasher = new VerificationCodeHasher() {
    @Override
    public String hash(String rawCode) {
      return rawCode;
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
      return true;
    }
  };

  private final VerificationCodeHasher rejectHasher = new VerificationCodeHasher() {
    @Override
    public String hash(String rawCode) {
      return rawCode;
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
      return false;
    }
  };

  private VerificationCode code(LocalDateTime now) {
    return VerificationCode.of("hashed-123456", now, Duration.ofMinutes(5));
  }

  private CredentialOwner owner() {
    return new UserCredentialOwner(UserNo.of("teller-1"));
  }

  private Mobile mobile() {
    return new Mobile("+8613800138000");
  }

  private SecondaryAuthSession newPendingSession(LocalDateTime now) {
    return SecondaryAuthSession.initiate(new SecondaryAuthSession.InitiateContext(
      new SecondaryAuthSessionId("s-1"),
      UserNo.of("teller-1"),
      owner(),
      UserNo.of("approver-1"),
      mobile(),
      null,
      code(now),
      Duration.ofMinutes(5),
      Duration.ofHours(2),
      UserNo.of("teller-1")));
  }

  private SecondaryAuthSession newAuthorizedSession(LocalDateTime now) {
    SecondaryAuthSession session = newPendingSession(now);
    session.authorize("123456", snapshot(now), identity(), acceptHasher, UserNo.of("teller-1"));
    return session;
  }

  private PermissionSnapshot snapshot(LocalDateTime now) {
    Permission p = new Permission(new BusinessCode("B"), new ActionCode("A"));
    return PermissionSnapshot.of(Set.of(p), now, Duration.ofSeconds(30));
  }

  private EffectiveIdentity identity() {
    return EffectiveIdentity.direct(UserNo.of("approver-1"));
  }

  @Nested
  @DisplayName("initiate 发起授权")
  class InitiateTest {

    @Test
    @DisplayName("发起后状态应当为 PENDING")
    void should_be_pending_when_initiated() {
      SecondaryAuthSession session = newPendingSession(LocalDateTime.now());
      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
    }

    @Test
    @DisplayName("发起后应当注册 SecondaryAuthInitiated 事件")
    void should_register_initiated_event() {
      SecondaryAuthSession session = newPendingSession(LocalDateTime.now());
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthInitiated".equals(e.eventType()));
    }

    @Test
    @DisplayName("柜员账号为 null 时应当抛异常")
    void should_throw_when_teller_null() {
      assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        new SecondaryAuthSession.InitiateContext(
          new SecondaryAuthSessionId("s-1"),
          null,
          owner(),
          UserNo.of("approver-1"),
          mobile(),
          null,
          code(LocalDateTime.now()),
          Duration.ofMinutes(5),
          Duration.ofHours(2),
          UserNo.of("teller-1"))))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("验证码为 null 时应当抛异常")
    void should_throw_when_code_null() {
      assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        new SecondaryAuthSession.InitiateContext(
          new SecondaryAuthSessionId("s-1"),
          UserNo.of("teller-1"),
          owner(),
          UserNo.of("approver-1"),
          mobile(),
          null,
          null,
          Duration.ofMinutes(5),
          Duration.ofHours(2),
          UserNo.of("teller-1"))))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("authorize 确认授权")
  class AuthorizeTest {

    @Test
    @DisplayName("验证码不匹配时应当抛 INVALID_VERIFICATION_CODE")
    void should_throw_when_code_not_match() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);
      PermissionSnapshot snap = snapshot(now);
      EffectiveIdentity effectiveId = identity();

      assertThatThrownBy(() -> session.authorize(
        "wrong-code", snap, effectiveId, rejectHasher, UserNo.of("teller-1")))
        .isInstanceOf(DomainException.class);
      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
    }

    @Test
    @DisplayName("验证码匹配时应当流转到 AUTHORIZED 并清空 verificationCode")
    void should_clear_code_when_authorized() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);
      PermissionSnapshot snap = snapshot(now);
      EffectiveIdentity effectiveId = identity();

      session.authorize("123456", snap, effectiveId, acceptHasher, UserNo.of("teller-1"));

      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
      assertThat(session.verificationCode()).isNull();
      assertThat(session.permissionSnapshot()).isEqualTo(snap);
      assertThat(session.effectiveIdentity()).isEqualTo(effectiveId);
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthCompleted".equals(e.eventType()));
    }

    @Test
    @DisplayName("非 PENDING 状态调用 authorize 应当抛异常")
    void should_throw_when_not_pending() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);
      PermissionSnapshot snap = snapshot(now);
      EffectiveIdentity effectiveId = identity();

      session.authorize("123456", snap, effectiveId, acceptHasher, UserNo.of("teller-1"));

      assertThatThrownBy(() -> session.authorize(
        "123456", snap, effectiveId, acceptHasher, UserNo.of("teller-1")))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("revoke 撤销授权")
  class RevokeTest {

    @Test
    @DisplayName("AUTHORIZED 状态撤销后应当流转到 REVOKED")
    void should_be_revoked_when_authorized() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newAuthorizedSession(now);

      session.revoke(UserNo.of("approver-1"), "经办人主动撤销");

      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
      assertThat(session.revokeReason()).isEqualTo("经办人主动撤销");
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthRevoked".equals(e.eventType()));
    }

    @Test
    @DisplayName("非 AUTHORIZED 状态调用 revoke 应当抛异常")
    void should_throw_when_revoke_not_authorized() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);

      assertThatThrownBy(() -> session.revoke(UserNo.of("approver-1"), "测试"))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("close 关闭会话")
  class CloseTest {

    @Test
    @DisplayName("AUTHORIZED 状态关闭后应当流转到 CLOSED")
    void should_be_closed_when_authorized() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newAuthorizedSession(now);

      session.close(UserNo.of("teller-1"));

      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthClosed".equals(e.eventType()));
    }
  }

  @Nested
  @DisplayName("expireIfTimeout 超时过期")
  class ExpireTest {

    @Test
    @DisplayName("PENDING 超时后应当流转到 EXPIRED")
    void should_expire_when_pending_timeout() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);

      // 模拟 6 分钟后（超过 5 分钟的 pendingTimeout）
      session.expireIfTimeout(now.plusMinutes(6));

      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.EXPIRED);
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthExpired".equals(e.eventType()));
    }

    @Test
    @DisplayName("未超时时不应当流转状态")
    void should_not_expire_when_not_timeout() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newPendingSession(now);

      session.expireIfTimeout(now.plusMinutes(4));

      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
    }

    @Test
    @DisplayName("终态状态调用 expireIfTimeout 应当不做任何事")
    void should_no_op_when_terminal() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = newAuthorizedSession(now);
      session.close(UserNo.of("teller-1"));

      session.expireIfTimeout(now.plusHours(3));

      // 仍然 CLOSED
      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
    }
  }
}
