package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.iam.types.LoginLogId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("authentication 域事件契约")
class AuthenticationEventsTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final UserId USER_ID = UserId.of(1001L);
  private static final CredentialId CREDENTIAL_ID = CredentialId.of(2001L);
  private static final LoginLogId LOGIN_LOG_ID = LoginLogId.of(3001L);
  private static final SecondaryAuthSessionId SESSION_ID = SecondaryAuthSessionId.of(4001L);
  private static final Long OWNER_ID = 1001L;
  private static final Long TELLER_ID = 5001L;
  private static final Long APPROVER_ID = 5002L;

  private static void assertDomainEventContract(DomainEvent event) {
    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    LocalDateTime before = LocalDateTime.now().minusSeconds(1);
    LocalDateTime after = LocalDateTime.now().plusSeconds(1);
    assertThat(event.occurredOn()).isBetween(before, after);
  }

  @Nested
  @DisplayName("UserCreatedEvent")
  class UserCreatedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      UserCreatedEvent event = UserCreatedEvent.of(
          USER_ID, ChannelType.INTERNET, "alice", "Alice");

      assertDomainEventContract(event);
      assertThat(event.userId()).isEqualTo(USER_ID);
      assertThat(event.channelType()).isEqualTo(ChannelType.INTERNET);
      assertThat(event.loginName()).isEqualTo("alice");
      assertThat(event.displayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      UserCreatedEvent e1 = UserCreatedEvent.of(
          USER_ID, ChannelType.INTERNET, "alice", "Alice");
      UserCreatedEvent e2 = UserCreatedEvent.of(
          USER_ID, ChannelType.INTERNET, "alice", "Alice");

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("UserEnabledEvent")
  class UserEnabledEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      UserEnabledEvent event = UserEnabledEvent.of(USER_ID, OPERATOR);

      assertDomainEventContract(event);
      assertThat(event.userId()).isEqualTo(USER_ID);
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      UserEnabledEvent e1 = UserEnabledEvent.of(USER_ID, OPERATOR);
      UserEnabledEvent e2 = UserEnabledEvent.of(USER_ID, OPERATOR);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("UserDisabledEvent")
  class UserDisabledEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      UserDisabledEvent event = UserDisabledEvent.of(USER_ID, "违规操作", OPERATOR);

      assertDomainEventContract(event);
      assertThat(event.userId()).isEqualTo(USER_ID);
      assertThat(event.reason()).isEqualTo("违规操作");
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      UserDisabledEvent e1 = UserDisabledEvent.of(USER_ID, "违规操作", OPERATOR);
      UserDisabledEvent e2 = UserDisabledEvent.of(USER_ID, "违规操作", OPERATOR);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("UserLoginSucceededEvent")
  class UserLoginSucceededEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      LocalDateTime loginTime = LocalDateTime.now().minusMinutes(1);

      UserLoginSucceededEvent event = UserLoginSucceededEvent.of(
          LOGIN_LOG_ID, USER_ID, ChannelType.HQ, "10.0.0.1", loginTime);

      assertDomainEventContract(event);
      assertThat(event.loginLogId()).isEqualTo(LOGIN_LOG_ID);
      assertThat(event.userId()).isEqualTo(USER_ID);
      assertThat(event.channelType()).isEqualTo(ChannelType.HQ);
      assertThat(event.loginIp()).isEqualTo("10.0.0.1");
      assertThat(event.loginTime()).isEqualTo(loginTime);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      LocalDateTime loginTime = LocalDateTime.now().minusMinutes(1);

      UserLoginSucceededEvent e1 = UserLoginSucceededEvent.of(
          LOGIN_LOG_ID, USER_ID, ChannelType.HQ, "10.0.0.1", loginTime);
      UserLoginSucceededEvent e2 = UserLoginSucceededEvent.of(
          LOGIN_LOG_ID, USER_ID, ChannelType.HQ, "10.0.0.1", loginTime);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("UserLoginFailedEvent")
  class UserLoginFailedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      UserLoginFailedEvent event = UserLoginFailedEvent.of(
          LOGIN_LOG_ID, USER_ID.longValue(), "bob", ChannelType.INTERNET,
          "192.168.1.1", "密码错误");

      assertDomainEventContract(event);
      assertThat(event.loginLogId()).isEqualTo(LOGIN_LOG_ID);
      assertThat(event.userId()).isEqualTo(1001L);
      assertThat(event.loginName()).isEqualTo("bob");
      assertThat(event.channelType()).isEqualTo(ChannelType.INTERNET);
      assertThat(event.loginIp()).isEqualTo("192.168.1.1");
      assertThat(event.reason()).isEqualTo("密码错误");
    }

    @Test
    @DisplayName("用户不存在场景 userId 为 null 仍可构建事件")
    void of_supportsNullUserId() {
      UserLoginFailedEvent event = UserLoginFailedEvent.of(
          LOGIN_LOG_ID, null, "ghost", ChannelType.INTERNET,
          "192.168.1.2", "用户不存在");

      assertDomainEventContract(event);
      assertThat(event.userId()).isNull();
      assertThat(event.loginName()).isEqualTo("ghost");
      assertThat(event.reason()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      UserLoginFailedEvent e1 = UserLoginFailedEvent.of(
          LOGIN_LOG_ID, USER_ID.longValue(), "bob", ChannelType.INTERNET,
          "192.168.1.1", "密码错误");
      UserLoginFailedEvent e2 = UserLoginFailedEvent.of(
          LOGIN_LOG_ID, USER_ID.longValue(), "bob", ChannelType.INTERNET,
          "192.168.1.1", "密码错误");

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("CredentialCreatedEvent")
  class CredentialCreatedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      CredentialCreatedEvent event = CredentialCreatedEvent.of(
          CREDENTIAL_ID, OWNER_ID, "USER", CredentialType.PASSWORD);

      assertDomainEventContract(event);
      assertThat(event.credentialId()).isEqualTo(CREDENTIAL_ID);
      assertThat(event.ownerId()).isEqualTo(OWNER_ID);
      assertThat(event.ownerType()).isEqualTo("USER");
      assertThat(event.credentialType()).isEqualTo(CredentialType.PASSWORD);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      CredentialCreatedEvent e1 = CredentialCreatedEvent.of(
          CREDENTIAL_ID, OWNER_ID, "USER", CredentialType.PASSWORD);
      CredentialCreatedEvent e2 = CredentialCreatedEvent.of(
          CREDENTIAL_ID, OWNER_ID, "USER", CredentialType.PASSWORD);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("CredentialChangedEvent")
  class CredentialChangedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      CredentialChangedEvent event = CredentialChangedEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.UKEY, OPERATOR);

      assertDomainEventContract(event);
      assertThat(event.credentialId()).isEqualTo(CREDENTIAL_ID);
      assertThat(event.ownerId()).isEqualTo(OWNER_ID);
      assertThat(event.credentialType()).isEqualTo(CredentialType.UKEY);
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      CredentialChangedEvent e1 = CredentialChangedEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.UKEY, OPERATOR);
      CredentialChangedEvent e2 = CredentialChangedEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.UKEY, OPERATOR);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("CredentialExpiredEvent")
  class CredentialExpiredEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      CredentialExpiredEvent event = CredentialExpiredEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.DYNAMIC_TOKEN);

      assertDomainEventContract(event);
      assertThat(event.credentialId()).isEqualTo(CREDENTIAL_ID);
      assertThat(event.ownerId()).isEqualTo(OWNER_ID);
      assertThat(event.credentialType()).isEqualTo(CredentialType.DYNAMIC_TOKEN);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      CredentialExpiredEvent e1 = CredentialExpiredEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.DYNAMIC_TOKEN);
      CredentialExpiredEvent e2 = CredentialExpiredEvent.of(
          CREDENTIAL_ID, OWNER_ID, CredentialType.DYNAMIC_TOKEN);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("SecondaryAuthInitiatedEvent")
  class SecondaryAuthInitiatedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      SecondaryAuthInitiatedEvent event = SecondaryAuthInitiatedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "CUST-001", "PLAN-A");

      assertDomainEventContract(event);
      assertThat(event.sessionId()).isEqualTo(SESSION_ID);
      assertThat(event.tellerId()).isEqualTo(TELLER_ID);
      assertThat(event.approverId()).isEqualTo(APPROVER_ID);
      assertThat(event.customerNo()).isEqualTo("CUST-001");
      assertThat(event.planId()).isEqualTo("PLAN-A");
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      SecondaryAuthInitiatedEvent e1 = SecondaryAuthInitiatedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "CUST-001", "PLAN-A");
      SecondaryAuthInitiatedEvent e2 = SecondaryAuthInitiatedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "CUST-001", "PLAN-A");

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("SecondaryAuthCompletedEvent")
  class SecondaryAuthCompletedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      LocalDateTime authorizedAt = LocalDateTime.now().minusSeconds(30);
      LocalDateTime expireAt = LocalDateTime.now().plusMinutes(5);

      SecondaryAuthCompletedEvent event = SecondaryAuthCompletedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "PLAN-A", authorizedAt, expireAt);

      assertDomainEventContract(event);
      assertThat(event.sessionId()).isEqualTo(SESSION_ID);
      assertThat(event.tellerId()).isEqualTo(TELLER_ID);
      assertThat(event.approverId()).isEqualTo(APPROVER_ID);
      assertThat(event.planId()).isEqualTo("PLAN-A");
      assertThat(event.authorizedAt()).isEqualTo(authorizedAt);
      assertThat(event.expireAt()).isEqualTo(expireAt);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      LocalDateTime authorizedAt = LocalDateTime.now().minusSeconds(30);
      LocalDateTime expireAt = LocalDateTime.now().plusMinutes(5);

      SecondaryAuthCompletedEvent e1 = SecondaryAuthCompletedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "PLAN-A", authorizedAt, expireAt);
      SecondaryAuthCompletedEvent e2 = SecondaryAuthCompletedEvent.of(
          SESSION_ID, TELLER_ID, APPROVER_ID, "PLAN-A", authorizedAt, expireAt);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("SecondaryAuthRevokedEvent")
  class SecondaryAuthRevokedEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      SecondaryAuthRevokedEvent event = SecondaryAuthRevokedEvent.of(
          SESSION_ID, TELLER_ID, "业务取消", OPERATOR);

      assertDomainEventContract(event);
      assertThat(event.sessionId()).isEqualTo(SESSION_ID);
      assertThat(event.tellerId()).isEqualTo(TELLER_ID);
      assertThat(event.reason()).isEqualTo("业务取消");
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      SecondaryAuthRevokedEvent e1 = SecondaryAuthRevokedEvent.of(
          SESSION_ID, TELLER_ID, "业务取消", OPERATOR);
      SecondaryAuthRevokedEvent e2 = SecondaryAuthRevokedEvent.of(
          SESSION_ID, TELLER_ID, "业务取消", OPERATOR);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }

  @Nested
  @DisplayName("SecondaryAuthExpiredEvent")
  class SecondaryAuthExpiredEventTest {

    @Test
    @DisplayName("of() 构建 DomainEvent 并保持字段")
    void of_buildsDomainEventAndPreservesFields() {
      SecondaryAuthExpiredEvent event = SecondaryAuthExpiredEvent.of(
          SESSION_ID, TELLER_ID);

      assertDomainEventContract(event);
      assertThat(event.sessionId()).isEqualTo(SESSION_ID);
      assertThat(event.tellerId()).isEqualTo(TELLER_ID);
    }

    @Test
    @DisplayName("相同参数生成不同的 eventId")
    void of_generatesUniqueEventIds() {
      SecondaryAuthExpiredEvent e1 = SecondaryAuthExpiredEvent.of(
          SESSION_ID, TELLER_ID);
      SecondaryAuthExpiredEvent e2 = SecondaryAuthExpiredEvent.of(
          SESSION_ID, TELLER_ID);

      assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
  }
}
