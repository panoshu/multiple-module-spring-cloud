package com.pension.permission.application.channel;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.application.channel.command.CloseSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ConfirmSecondaryAuthCommand;
import com.pension.permission.application.channel.command.InitiateSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ResendCodeCommand;
import com.pension.permission.application.channel.command.RevokeSecondaryAuthCommand;
import com.pension.permission.application.channel.config.SecondaryAuthConfig;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecondaryAuthAppService 应用服务测试")
class SecondaryAuthAppServiceTest {

  private static final UserNo TELLER_NO = UserNo.of("teller-1");
  private static final UserNo APPROVER_NO = UserNo.of("approver-1");
  private static final SecondaryAuthSessionId SESSION_ID = new SecondaryAuthSessionId("auth-001");
  private static final Mobile MOBILE = new Mobile("+8613800138000");
  private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);
  private static final int VERIFICATION_MAX_ATTEMPTS = 3;
  private static final int VERIFICATION_CODE_LENGTH = 6;

  @Mock private SecondaryAuthSessionRepository sessionRepository;
  @Mock private VerificationCodeHasher codeHasher;
  @Mock private SecondaryAuthConfig config;
  @Mock private IdService idService;
  @Mock private EventBus eventBus;

  private SecondaryAuthAppService service;

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

  @BeforeEach
  void setUp() {
    lenient().when(config.getPendingTimeout()).thenReturn(PENDING_TIMEOUT);
    lenient().when(config.getSessionTimeout()).thenReturn(SESSION_TIMEOUT);
    lenient().when(config.getVerificationMaxAttempts()).thenReturn(VERIFICATION_MAX_ATTEMPTS);
    lenient().when(config.getVerificationCodeLength()).thenReturn(VERIFICATION_CODE_LENGTH);
    service = new SecondaryAuthAppService(
      sessionRepository, codeHasher, config, idService, eventBus);
  }

  private CredentialOwner owner() {
    return new UserCredentialOwner(TELLER_NO);
  }

  private PermissionSnapshot snapshot() {
    Permission p = new Permission(new BusinessCode("B"), new ActionCode("A"));
    return PermissionSnapshot.of(Set.of(p), LocalDateTime.now(), Duration.ofSeconds(30));
  }

  private EffectiveIdentity identity() {
    return new EffectiveIdentity(APPROVER_NO, TELLER_NO, true);
  }

  private SecondaryAuthSession newPendingSession(SecondaryAuthSessionId id) {
    return SecondaryAuthSession.initiate(new SecondaryAuthSession.InitiateContext(
      id, TELLER_NO, owner(), APPROVER_NO, MOBILE, null,
      VerificationCode.of("hashed-123456", LocalDateTime.now(),
        PENDING_TIMEOUT, VERIFICATION_MAX_ATTEMPTS),
      PENDING_TIMEOUT, SESSION_TIMEOUT, TELLER_NO));
  }

  private SecondaryAuthSession newPendingSession() {
    return newPendingSession(SESSION_ID);
  }

  private SecondaryAuthSession newAuthorizedSession(SecondaryAuthSessionId id) {
    SecondaryAuthSession session = newPendingSession(id);
    session.authorize("123456", snapshot(), identity(), acceptHasher, TELLER_NO);
    return session;
  }

  private SecondaryAuthSession newAuthorizedSession() {
    return newAuthorizedSession(SESSION_ID);
  }

  private SecondaryAuthSession newExpiredSession() {
    SecondaryAuthSession session = newPendingSession();
    session.expireIfTimeout(LocalDateTime.now().plusMinutes(6));
    return session;
  }

  private SecondaryAuthSession newRevokedSession() {
    SecondaryAuthSession session = newAuthorizedSession();
    session.revoke(APPROVER_NO, "测试撤销");
    return session;
  }

  @Nested
  @DisplayName("initiate 发起二次授权")
  class InitiateTest {

    @Test
    @DisplayName("已有活跃会话时应抛 BusinessException")
    void shouldThrowWhenActiveSessionExists() {
      InitiateSecondaryAuthCommand cmd = new InitiateSecondaryAuthCommand(
        TELLER_NO, owner(), APPROVER_NO, MOBILE, null);
      when(sessionRepository.findActiveByTeller(TELLER_NO))
        .thenReturn(Optional.of(newPendingSession()));

      assertThatThrownBy(() -> service.initiate(cmd))
        .isInstanceOf(BusinessException.class);

      verify(sessionRepository, never()).save(any());
      verify(idService, never()).nextId(any(Class.class));
    }

    @Test
    @DisplayName("正常创建时应生成验证码、哈希、创建会话、保存并发布事件")
    void shouldCreateSessionSuccessfully() {
      InitiateSecondaryAuthCommand cmd = new InitiateSecondaryAuthCommand(
        TELLER_NO, owner(), APPROVER_NO, MOBILE, null);
      when(sessionRepository.findActiveByTeller(TELLER_NO)).thenReturn(Optional.empty());
      when(codeHasher.hash(anyString())).thenReturn("hashed-code");
      when(idService.nextId(SecondaryAuthSessionId.class)).thenReturn(SESSION_ID);

      SecondaryAuthSessionId result = service.initiate(cmd);

      assertThat(result).isEqualTo(SESSION_ID);
      verify(codeHasher).hash(anyString());
      verify(idService).nextId(SecondaryAuthSessionId.class);

      ArgumentCaptor<SecondaryAuthSession> sessionCaptor =
        ArgumentCaptor.forClass(SecondaryAuthSession.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      SecondaryAuthSession saved = sessionCaptor.getValue();
      assertThat(saved.status()).isEqualTo(SecondaryAuthStatus.PENDING);
      assertThat(saved.tellerAccountId()).isEqualTo(TELLER_NO);
      assertThat(saved.approverAccountId()).isEqualTo(APPROVER_NO);

      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("confirm 确认二次授权")
  class ConfirmTest {

    @Test
    @DisplayName("PENDING状态时应授权通过")
    void shouldAuthorizeWhenPending() {
      SecondaryAuthSession session = newPendingSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      when(codeHasher.matches(anyString(), anyString())).thenReturn(true);
      ConfirmSecondaryAuthCommand cmd = new ConfirmSecondaryAuthCommand(
        SESSION_ID, "123456", snapshot(), TELLER_NO);

      service.confirm(cmd);

      ArgumentCaptor<SecondaryAuthSession> sessionCaptor =
        ArgumentCaptor.forClass(SecondaryAuthSession.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      SecondaryAuthSession saved = sessionCaptor.getValue();
      assertThat(saved.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
      assertThat(saved.permissionSnapshot()).isNotNull();
      assertThat(saved.effectiveIdentity()).isNotNull();
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("AUTHORIZED状态时应抛 BusinessException")
    void shouldThrowWhenAuthorized() {
      SecondaryAuthSession session = newAuthorizedSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      ConfirmSecondaryAuthCommand cmd = new ConfirmSecondaryAuthCommand(
        SESSION_ID, "123456", snapshot(), TELLER_NO);

      assertThatThrownBy(() -> service.confirm(cmd))
        .isInstanceOf(BusinessException.class);

      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("EXPIRED状态时应抛 BusinessException")
    void shouldThrowWhenExpired() {
      SecondaryAuthSession session = newExpiredSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      ConfirmSecondaryAuthCommand cmd = new ConfirmSecondaryAuthCommand(
        SESSION_ID, "123456", snapshot(), TELLER_NO);

      assertThatThrownBy(() -> service.confirm(cmd))
        .isInstanceOf(BusinessException.class);

      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("REVOKED状态时应抛 BusinessException")
    void shouldThrowWhenRevoked() {
      SecondaryAuthSession session = newRevokedSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      ConfirmSecondaryAuthCommand cmd = new ConfirmSecondaryAuthCommand(
        SESSION_ID, "123456", snapshot(), TELLER_NO);

      assertThatThrownBy(() -> service.confirm(cmd))
        .isInstanceOf(BusinessException.class);

      verify(sessionRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("resendCode 重发验证码")
  class ResendCodeTest {

    @Test
    @DisplayName("应生成新验证码并调用resendVerificationCode")
    void shouldGenerateNewCodeAndResend() {
      SecondaryAuthSession session = newPendingSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      when(codeHasher.hash(anyString())).thenReturn("new-hashed-code");
      ResendCodeCommand cmd = new ResendCodeCommand(SESSION_ID, TELLER_NO);

      service.resendCode(cmd);

      verify(codeHasher).hash(anyString());

      ArgumentCaptor<SecondaryAuthSession> sessionCaptor =
        ArgumentCaptor.forClass(SecondaryAuthSession.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      SecondaryAuthSession saved = sessionCaptor.getValue();
      assertThat(saved.status()).isEqualTo(SecondaryAuthStatus.PENDING);
      assertThat(saved.verificationCode()).isNotNull();

      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("revoke 撤销二次授权")
  class RevokeTest {

    @Test
    @DisplayName("应调用session.revoke并保存")
    void shouldRevokeAndSave() {
      SecondaryAuthSession session = newAuthorizedSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      RevokeSecondaryAuthCommand cmd = new RevokeSecondaryAuthCommand(
        SESSION_ID, APPROVER_NO, "测试撤销");

      service.revoke(cmd);

      ArgumentCaptor<SecondaryAuthSession> sessionCaptor =
        ArgumentCaptor.forClass(SecondaryAuthSession.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      SecondaryAuthSession saved = sessionCaptor.getValue();
      assertThat(saved.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
      assertThat(saved.revokeReason()).isEqualTo("测试撤销");
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("close 关闭二次授权会话")
  class CloseTest {

    @Test
    @DisplayName("应调用session.close并保存")
    void shouldCloseAndSave() {
      SecondaryAuthSession session = newAuthorizedSession();
      when(sessionRepository.loadOrThrow(SESSION_ID)).thenReturn(session);
      CloseSecondaryAuthCommand cmd = new CloseSecondaryAuthCommand(SESSION_ID, TELLER_NO);

      service.close(cmd);

      ArgumentCaptor<SecondaryAuthSession> sessionCaptor =
        ArgumentCaptor.forClass(SecondaryAuthSession.class);
      verify(sessionRepository).save(sessionCaptor.capture());
      SecondaryAuthSession saved = sessionCaptor.getValue();
      assertThat(saved.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }

  @Nested
  @DisplayName("revokeAllAuthorizedByApprover 紧急收权")
  class RevokeAllAuthorizedByApproverTest {

    @Test
    @DisplayName("应撤销所有AUTHORIZED会话")
    void shouldRevokeAllAuthorizedSessions() {
      SecondaryAuthSession session1 = newAuthorizedSession(new SecondaryAuthSessionId("auth-1"));
      SecondaryAuthSession session2 = newAuthorizedSession(new SecondaryAuthSessionId("auth-2"));
      when(sessionRepository.findAuthorizedByApprover(APPROVER_NO))
        .thenReturn(List.of(session1, session2));

      service.revokeAllAuthorizedByApprover(APPROVER_NO);

      verify(sessionRepository, times(2)).save(any(SecondaryAuthSession.class));
      verify(eventBus, atLeastOnce()).publish(any(DomainEvent.class));
    }
  }
}
