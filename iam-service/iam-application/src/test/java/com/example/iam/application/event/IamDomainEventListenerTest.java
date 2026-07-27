package com.example.iam.application.event;

import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.event.UserDisabledEvent;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.event.PermissionRuleCreatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationCreatedEvent;
import com.example.iam.types.CredentialId;
import com.example.iam.types.PermissionRuleId;
import com.example.iam.types.PlanDelegationId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IamDomainEventListener} 单元测试。
 *
 * <p>覆盖关键领域事件的后续处理流程,验证跨聚合协作(撤销凭据、撤销二次授权会话、
 * 踢人下线、缓存失效)是否正确执行。
 *
 * @author iam-service
 */
@DisplayName("IAM 领域事件监听器测试")
@ExtendWith(MockitoExtension.class)
class IamDomainEventListenerTest {

  private static final Long USER_ID = 9001L;
  private static final String REASON = "违规操作";
  private static final UserNo OPERATOR = UserNo.of("admin");
  private static final String DELEGATOR_PLAN_NO = "PLAN_A";
  private static final String DELEGATEE_PLAN_NO = "PLAN_B";

  @Mock private UserRepository userRepository;
  @Mock private CredentialRepository credentialRepository;
  @Mock private SecondaryAuthSessionRepository secondaryAuthSessionRepository;
  @Mock private ChannelSessionPort channelSessionPort;
  @Mock private PermissionCachePort permissionCachePort;

  @InjectMocks
  private IamDomainEventListener listener;

  @Nested
  @DisplayName("onUserDisabled 用户已禁用事件")
  class OnUserDisabledTest {

    @Test
    @DisplayName("撤销凭据 + 撤销二次授权会话 + 踢人下线 + 失效用户权限缓存")
    void should_revoke_credentials_session_kickout_and_evict_cache() {
      UserDisabledEvent event = UserDisabledEvent.of(UserId.of(USER_ID), REASON, OPERATOR);
      User user = buildUser(ChannelType.BRANCH);
      Credential credential = buildCredential();
      SecondaryAuthSession session = buildAuthorizedSession();
      when(userRepository.load(UserId.of(USER_ID))).thenReturn(Optional.of(user));
      when(credentialRepository.findAllByOwner(USER_ID, "BRANCH_USER"))
          .thenReturn(List.of(credential));
      when(secondaryAuthSessionRepository.findEffectiveByTeller(USER_ID))
          .thenReturn(Optional.of(session));

      listener.onUserDisabled(event);

      verify(credentialRepository).save(credential);
      assertThatCredentialRevoked(credential);
      verify(secondaryAuthSessionRepository).save(session);
      assertThatSessionRevoked(session);
      verify(channelSessionPort).kickout(USER_ID, ChannelType.BRANCH);
      verify(permissionCachePort).evictByUser(USER_ID);
    }

    @Test
    @DisplayName("用户无生效二次授权会话时不撤销会话,仍执行踢人与缓存失效")
    void should_skip_session_revoke_when_no_effective_session() {
      UserDisabledEvent event = UserDisabledEvent.of(UserId.of(USER_ID), REASON, OPERATOR);
      User user = buildUser(ChannelType.INTERNET);
      when(userRepository.load(UserId.of(USER_ID))).thenReturn(Optional.of(user));
      when(credentialRepository.findAllByOwner(USER_ID, "INTERNET_USER"))
          .thenReturn(List.of());
      when(secondaryAuthSessionRepository.findEffectiveByTeller(USER_ID))
          .thenReturn(Optional.empty());

      listener.onUserDisabled(event);

      verify(secondaryAuthSessionRepository, never()).save(any());
      verify(channelSessionPort).kickout(USER_ID, ChannelType.INTERNET);
      verify(permissionCachePort).evictByUser(USER_ID);
    }

    private void assertThatCredentialRevoked(Credential credential) {
      org.assertj.core.api.Assertions.assertThat(credential.status())
          .isEqualTo(CredentialStatus.REVOKED);
    }

    private void assertThatSessionRevoked(SecondaryAuthSession session) {
      org.assertj.core.api.Assertions.assertThat(session.status())
          .isEqualTo(SecondaryAuthStatus.REVOKED);
    }
  }

  @Nested
  @DisplayName("onPermissionRuleCreated 权限规则已创建事件")
  class OnPermissionRuleCreatedTest {

    @Test
    @DisplayName("失效全部权限缓存")
    void should_evict_all_cache() {
      PermissionRuleCreatedEvent event = PermissionRuleCreatedEvent.of(
          PermissionRuleId.of(1001L), "RULE_001",
          SubjectType.CUSTOMER, "CUST001",
          BusinessCode.of("ANNUITY_ESTABLISH"),
          OverrideMode.ADD, 1,
          OPERATOR);

      listener.onPermissionRuleCreated(event);

      verify(permissionCachePort).evictAll();
    }
  }

  @Nested
  @DisplayName("onPlanDelegationCreated 计划代办关系已创建事件")
  class OnPlanDelegationCreatedTest {

    @Test
    @DisplayName("失效授权方与被授权方计划的权限缓存")
    void should_evict_both_plan_caches() {
      PlanDelegationCreatedEvent event = PlanDelegationCreatedEvent.of(
          PlanDelegationId.of(3001L), "DLG_001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, OPERATOR);

      listener.onPlanDelegationCreated(event);

      verify(permissionCachePort).evictByPlan(DELEGATOR_PLAN_NO);
      verify(permissionCachePort).evictByPlan(DELEGATEE_PLAN_NO);
    }
  }

  private User buildUser(ChannelType channelType) {
    return User.reconstitute(
        UserId.of(USER_ID), channelType, "user001", "用户",
        com.example.iam.domain.authentication.aggregate.valueobject.UserStatus.ACTIVE,
        null, null, null,
        OPERATOR, OPERATOR,
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private Credential buildCredential() {
    return Credential.reconstitute(
        CredentialId.of(4001L),
        "BRANCH_USER", USER_ID, CredentialType.PASSWORD,
        "hashed-pwd", null, java.util.Map.of(),
        CredentialStatus.ACTIVE, null,
        OPERATOR, OPERATOR,
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private SecondaryAuthSession buildAuthorizedSession() {
    return SecondaryAuthSession.reconstitute(
        SecondaryAuthSessionId.of(8001L),
        USER_ID, 9002L, "CUST001", "PLAN001",
        Set.of(PermissionCode.of("BIZ.HANDLE")),
        SecondaryAuthStatus.AUTHORIZED,
        LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(7), null,
        OPERATOR, OPERATOR,
        LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(1),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
