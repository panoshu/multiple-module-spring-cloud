package com.pension.permission.application.channel;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.event.SecondaryAuthCompleted;
import com.pension.permission.domain.channel.event.SecondaryAuthRevoked;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.domain.channel.service.ChannelAccessPolicy;
import com.pension.permission.domain.channel.service.IdentityResolutionService;
import com.pension.permission.domain.channel.service.PlanSelectionStrategy;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.channel.valueobject.AllPlans;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;
import com.pension.permission.types.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionApplicationService 应用服务测试")
class SessionApplicationServiceTest {

  private static final UserNo TELLER_NO = UserNo.of("teller-1");
  private static final UserNo APPROVER_NO = UserNo.of("approver-1");
  private static final UserNo OPERATOR_NO = UserNo.of("operator-1");
  private static final SessionId SESSION_ID = new SessionId("session-token-001");
  private static final String TOKEN_VALUE = "session-token-001";
  private static final AnnuityChannel ONLINE_CHANNEL = AnnuityChannel.NETAPP;
  private static final AnnuityChannel BRANCH_CHANNEL = AnnuityChannel.BANK_BRANCH;
  private static final Mobile MOBILE = new Mobile("+8613800138000");

  @Mock
  private SessionRepository sessionRepository;
  @Mock
  private IdentityResolutionService identityResolutionService;
  @Mock
  private LoginTokenService loginTokenService;
  @Mock
  private EventBus eventBus;
  @Mock
  private PlanSelectionStrategy netappStrategy;
  @Mock
  private ChannelAccessPolicy channelAccessPolicy;

  private SessionApplicationService service;

  @BeforeEach
  void setUp() {
    Map<AnnuityChannel, PlanSelectionStrategy> strategiesByChannel =
      Map.of(ONLINE_CHANNEL, netappStrategy);
    service = new SessionApplicationService(
      sessionRepository,
      identityResolutionService,
      loginTokenService,
      strategiesByChannel,
      channelAccessPolicy,
      eventBus);
    // 默认：渠道过滤器原样返回输入（模拟客户已开通所有渠道）
    org.mockito.Mockito.lenient()
      .when(channelAccessPolicy.filterPlansByChannel(any(), any()))
      .thenAnswer(inv -> inv.getArgument(0));
  }

  private Session activeSession(UserNo accountId, AnnuityChannel channel) {
    return Session.create(
      SESSION_ID,
      accountId,
      accountId,
      channel,
      EffectiveIdentity.direct(accountId),
      Duration.ofHours(8));
  }

  @Nested
  @DisplayName("openSession 直接建会话")
  class OpenSessionTest {

    @Test
    @DisplayName("应签发token、创建Session、保存并发布事件")
    void shouldIssueTokenSaveSessionAndPublishEvent() {
      UserNo accountId = UserNo.of("user-1");
      OpenSessionCommand command = new OpenSessionCommand(accountId, ONLINE_CHANNEL);
      when(loginTokenService.issueToken(accountId, ONLINE_CHANNEL)).thenReturn(TOKEN_VALUE);

      SessionId sessionId = service.openSession(command);

      assertThat(sessionId.value()).isEqualTo(TOKEN_VALUE);

      verify(loginTokenService).issueToken(accountId, ONLINE_CHANNEL);

      ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      Session saved = sessionCaptor.getValue();
      assertThat(saved.id().value()).isEqualTo(TOKEN_VALUE);
      assertThat(saved.primaryAccountId()).isEqualTo(accountId);
      assertThat(saved.channel()).isEqualTo(ONLINE_CHANNEL);
      assertThat(saved.domainEvents()).isNotEmpty();

      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("openSessionWithCredential 凭证登录")
  class OpenSessionWithCredentialTest {

    private final CredentialOwner owner = new UserCredentialOwner(UserNo.of("user-1"));

    @Test
    @DisplayName("凭证校验通过时应创建会话")
    void shouldCreateSessionWhenCredentialVerified() {
      OpenSessionWithCredentialCommand command = new OpenSessionWithCredentialCommand(
        owner, ONLINE_CHANNEL, "proof-001", MOBILE);
      UserNo resolvedAccount = UserNo.of("user-1");
      when(identityResolutionService.resolve(owner, ONLINE_CHANNEL, "proof-001", MOBILE))
        .thenReturn(Optional.of(resolvedAccount));
      when(loginTokenService.issueToken(resolvedAccount, ONLINE_CHANNEL)).thenReturn(TOKEN_VALUE);

      SessionId sessionId = service.openSessionWithCredential(command);

      assertThat(sessionId.value()).isEqualTo(TOKEN_VALUE);
      verify(identityResolutionService).resolve(owner, ONLINE_CHANNEL, "proof-001", MOBILE);
      verify(loginTokenService).issueToken(resolvedAccount, ONLINE_CHANNEL);
      verify(sessionRepository).save(any(Session.class));
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("凭证校验失败时应抛 SecurityException")
    void shouldThrowSecurityExceptionWhenCredentialFailed() {
      OpenSessionWithCredentialCommand command = new OpenSessionWithCredentialCommand(
        owner, ONLINE_CHANNEL, "bad-proof", MOBILE);
      when(identityResolutionService.resolve(owner, ONLINE_CHANNEL, "bad-proof", MOBILE))
        .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.openSessionWithCredential(command))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("凭证校验不通过");

      verify(identityResolutionService).resolve(owner, ONLINE_CHANNEL, "bad-proof", MOBILE);
      verify(loginTokenService, never()).issueToken(any(), any());
      verify(sessionRepository, never()).save(any(Session.class));
      verify(eventBus, never()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("logout 登出")
  class LogoutTest {

    @Test
    @DisplayName("应关闭会话、保存并使token失效")
    void shouldCloseSessionSaveAndInvalidateToken() {
      Session session = activeSession(OPERATOR_NO, ONLINE_CHANNEL);
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      LogoutCommand command = new LogoutCommand(SESSION_ID, OPERATOR_NO);

      service.logout(command);

      ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      Session saved = sessionCaptor.getValue();
      assertThat(saved.status().name()).isEqualTo("CLOSED");
      verify(loginTokenService).invalidateToken(TOKEN_VALUE);
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("listSelectablePlans 查询可选计划范围")
  class ListSelectablePlansTest {

    @Test
    @DisplayName("AllPlans 范围应原样返回，不经过渠道过滤")
    void shouldReturnAllPlansAsIsWithoutChannelFilter() {
      Session session = activeSession(OPERATOR_NO, ONLINE_CHANNEL);
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      SelectablePlanScope scope = new AllPlans();
      when(netappStrategy.listSelectablePlans(session.effectiveIdentity())).thenReturn(scope);

      SelectablePlanScope result = service.listSelectablePlans(SESSION_ID);

      assertThat(result).isSameAs(scope);
      verify(netappStrategy).listSelectablePlans(session.effectiveIdentity());
      verify(channelAccessPolicy, never()).filterPlansByChannel(any(), any());
    }

    @Test
    @DisplayName("EnumeratedPlans 范围应经渠道过滤器过滤")
    void shouldFilterEnumeratedPlansByChannel() {
      Session session = activeSession(OPERATOR_NO, ONLINE_CHANNEL);
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      List<PlanNo> rawPlans = List.of(
        PlanNo.of("plan-1"), PlanNo.of("plan-2"), PlanNo.of("plan-3"));
      when(netappStrategy.listSelectablePlans(session.effectiveIdentity()))
        .thenReturn(new EnumeratedPlans(rawPlans));
      List<PlanNo> filteredPlans = List.of(PlanNo.of("plan-1"), PlanNo.of("plan-2"));
      when(channelAccessPolicy.filterPlansByChannel(rawPlans, ONLINE_CHANNEL))
        .thenReturn(filteredPlans);

      SelectablePlanScope result = service.listSelectablePlans(SESSION_ID);

      assertThat(result).isInstanceOf(EnumeratedPlans.class);
      assertThat(((EnumeratedPlans) result).plans()).containsExactlyElementsOf(filteredPlans);
      verify(channelAccessPolicy).filterPlansByChannel(rawPlans, ONLINE_CHANNEL);
    }

    @Test
    @DisplayName("策略未注册时应抛 IllegalStateException")
    void shouldThrowWhenStrategyNotRegistered() {
      Session session = activeSession(OPERATOR_NO, BRANCH_CHANNEL);
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);

      assertThatThrownBy(() -> service.listSelectablePlans(SESSION_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(BRANCH_CHANNEL.toString());

      verify(netappStrategy, never()).listSelectablePlans(any());
    }
  }

  @Nested
  @DisplayName("selectPlan 选择计划")
  class SelectPlanTest {

    @Test
    @DisplayName("应更新会话并保存")
    void shouldUpdateSessionAndSave() {
      Session session = activeSession(OPERATOR_NO, ONLINE_CHANNEL);
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      SelectPlanCommand command = new SelectPlanCommand(
        SESSION_ID, com.example.shared.identifier.id.PlanNo.of("plan-001"), OPERATOR_NO);

      service.selectPlan(command);

      ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      Session saved = sessionCaptor.getValue();
      assertThat(saved.selectedPlanId())
        .isEqualTo(com.example.shared.identifier.id.PlanNo.of("plan-001"));
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("onSecondaryAuthCompleted 二次授权完成事件")
  class OnSecondaryAuthCompletedTest {

    @Test
    @DisplayName("应找到柜员会话并应用二次授权")
    void shouldApplySecondaryAuthToTellerSession() {
      Session session = activeSession(TELLER_NO, BRANCH_CHANNEL);
      when(sessionRepository.findActiveByPrimaryAccountIdAndChannel(TELLER_NO, BRANCH_CHANNEL))
        .thenReturn(Optional.of(session));

      SecondaryAuthSessionId authSessionId = new SecondaryAuthSessionId("auth-1");
      EffectiveIdentity elevated = new EffectiveIdentity(APPROVER_NO, TELLER_NO, true);
      SecondaryAuthCompleted event = SecondaryAuthCompleted.of(
        authSessionId, TELLER_NO, APPROVER_NO, elevated, null, APPROVER_NO);

      service.onSecondaryAuthCompleted(event);

      ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      Session saved = sessionCaptor.getValue();
      assertThat(saved.secondaryAuthSessionId()).isEqualTo(authSessionId);
      assertThat(saved.effectiveIdentity()).isEqualTo(elevated);
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("柜员无活跃会话时应抛 BusinessException")
    void shouldThrowWhenNoActiveSession() {
      when(sessionRepository.findActiveByPrimaryAccountIdAndChannel(TELLER_NO, BRANCH_CHANNEL))
        .thenReturn(Optional.empty());

      SecondaryAuthSessionId authSessionId = new SecondaryAuthSessionId("auth-1");
      EffectiveIdentity elevated = new EffectiveIdentity(APPROVER_NO, TELLER_NO, true);
      SecondaryAuthCompleted event = SecondaryAuthCompleted.of(
        authSessionId, TELLER_NO, APPROVER_NO, elevated, null, APPROVER_NO);

      assertThatThrownBy(() -> service.onSecondaryAuthCompleted(event))
        .isInstanceOf(BusinessException.class);

      verify(sessionRepository, never()).save(any(Session.class));
    }
  }

  @Nested
  @DisplayName("onSecondaryAuthRevoked 二次授权撤销事件")
  class OnSecondaryAuthRevokedTest {

    @Test
    @DisplayName("应清除柜员会话上的二次授权绑定")
    void shouldClearSecondaryAuthBinding() {
      Session session = activeSession(TELLER_NO, BRANCH_CHANNEL);
      SecondaryAuthSessionId authSessionId = new SecondaryAuthSessionId("auth-1");
      EffectiveIdentity elevated = new EffectiveIdentity(APPROVER_NO, TELLER_NO, true);
      session.applySecondaryAuth(authSessionId, elevated, TELLER_NO);
      when(sessionRepository.findActiveByPrimaryAccountIdAndChannel(TELLER_NO, BRANCH_CHANNEL))
        .thenReturn(Optional.of(session));

      SecondaryAuthRevoked event = SecondaryAuthRevoked.of(
        authSessionId, TELLER_NO, APPROVER_NO, "经办人撤销", APPROVER_NO);

      service.onSecondaryAuthRevoked(event);

      ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      Session saved = sessionCaptor.getValue();
      assertThat(saved.secondaryAuthSessionId()).isNull();
      assertThat(saved.effectiveIdentity()).isEqualTo(EffectiveIdentity.direct(TELLER_NO));
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("无活跃会话时应静默跳过")
    void shouldSilentlySkipWhenNoActiveSession() {
      when(sessionRepository.findActiveByPrimaryAccountIdAndChannel(TELLER_NO, BRANCH_CHANNEL))
        .thenReturn(Optional.empty());

      SecondaryAuthSessionId authSessionId = new SecondaryAuthSessionId("auth-1");
      SecondaryAuthRevoked event = SecondaryAuthRevoked.of(
        authSessionId, TELLER_NO, APPROVER_NO, "经办人撤销", APPROVER_NO);

      service.onSecondaryAuthRevoked(event);

      verify(sessionRepository, never()).save(any(Session.class));
      verify(eventBus, never()).publish(any(DomainEvent.class));
    }
  }
}
