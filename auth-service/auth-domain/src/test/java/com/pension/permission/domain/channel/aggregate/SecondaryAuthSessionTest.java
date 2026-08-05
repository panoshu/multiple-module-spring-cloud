package com.pension.permission.domain.channel.aggregate;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecondaryAuthSession 聚合根测试")
class SecondaryAuthSessionTest {

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
    return SecondaryAuthSession.initiate(
      new SecondaryAuthSessionId("s-1"),
      UserNo.of("teller-1"),
      owner(),
      UserNo.of("approver-1"),
      mobile(),
      null,
      code(now),
      Duration.ofMinutes(5),
      Duration.ofHours(2),
      UserNo.of("teller-1"));
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
        new SecondaryAuthSessionId("s-1"),
        null,
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        code(LocalDateTime.now()),
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1")))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("验证码为 null 时应当抛异常")
    void should_throw_when_code_null() {
      assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        new SecondaryAuthSessionId("s-1"),
        UserNo.of("teller-1"),
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        null,
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1")))
        .isInstanceOf(NullPointerException.class);
    }
  }
}
