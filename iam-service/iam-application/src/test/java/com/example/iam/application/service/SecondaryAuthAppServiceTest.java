package com.example.iam.application.service;

import com.example.iam.api.command.ConfirmSecondaryAuthCommand;
import com.example.iam.api.command.InitiateSecondaryAuthCommand;
import com.example.iam.api.command.RevokeSecondaryAuthCommand;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SecondaryAuthAppService} 单元测试。
 *
 * <p>覆盖二次授权会话的发起、确认、撤销、查询流程,验证密码校验、权限快照冻结
 * 与渠道会话同步等关键协作。
 *
 * @author iam-service
 */
@DisplayName("二次授权应用服务测试")
@ExtendWith(MockitoExtension.class)
class SecondaryAuthAppServiceTest {

  private static final Long TELLER_ID = 8001L;
  private static final Long APPROVER_ID = 8002L;
  private static final Long SESSION_ID_VALUE = 9001L;
  private static final String APPROVER_LOGIN_NAME = "approver001";
  private static final String CUSTOMER_NO = "CUST001";
  private static final String PLAN_ID = "PLAN001";
  private static final String RAW_PASSWORD = "plain-pwd";
  private static final String HASHED_PASSWORD = "hashed-pwd";
  private static final String OPERATOR = "admin";
  private static final String REVOKE_REASON = "业务办理完成";

  @Mock private SecondaryAuthSessionRepository sessionRepository;
  @Mock private UserRepository userRepository;
  @Mock private CredentialRepository credentialRepository;
  @Mock private PermissionResolver permissionResolver;
  @Mock private ChannelSessionPort channelSessionPort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;
  @Mock private PasswordEncryptorPort passwordEncryptorPort;

  @InjectMocks
  private SecondaryAuthAppService secondaryAuthAppService;

  @Nested
  @DisplayName("initiate 发起二次授权")
  class InitiateTest {

    @Test
    @DisplayName("发起成功:生成会话 ID、保存并返回 PENDING 状态 DTO")
    void should_initiate_when_approver_exists() {
      InitiateSecondaryAuthCommand command = new InitiateSecondaryAuthCommand(
          APPROVER_LOGIN_NAME, CUSTOMER_NO, PLAN_ID, CUSTOMER_NO);
      User approver = buildUser(UserStatus.ACTIVE);
      when(channelSessionPort.currentUserId()).thenReturn(TELLER_ID);
      when(userRepository.findByLoginName(APPROVER_LOGIN_NAME, ChannelType.BRANCH))
          .thenReturn(Optional.of(approver));
      when(idService.nextLongId(SecondaryAuthSessionId.class, "IAM_2ND_AUTH"))
          .thenReturn(SecondaryAuthSessionId.of(SESSION_ID_VALUE));

      SecondaryAuthSessionDTO dto = secondaryAuthAppService.initiate(command);

      assertThat(dto.sessionId()).isEqualTo(SESSION_ID_VALUE);
      assertThat(dto.tellerId()).isEqualTo(TELLER_ID);
      assertThat(dto.approverId()).isEqualTo(APPROVER_ID);
      assertThat(dto.status()).isEqualTo("PENDING");
      verify(sessionRepository).save(any(SecondaryAuthSession.class));
    }

    @Test
    @DisplayName("经办人不存在时抛业务异常,不生成会话 ID")
    void should_throw_when_approver_not_found() {
      InitiateSecondaryAuthCommand command = new InitiateSecondaryAuthCommand(
          APPROVER_LOGIN_NAME, CUSTOMER_NO, PLAN_ID, CUSTOMER_NO);
      when(channelSessionPort.currentUserId()).thenReturn(TELLER_ID);
      when(userRepository.findByLoginName(APPROVER_LOGIN_NAME, ChannelType.BRANCH))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> secondaryAuthAppService.initiate(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), anyString());
      verify(sessionRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("confirm 确认二次授权")
  class ConfirmTest {

    @Test
    @DisplayName("确认成功:校验密码、冻结权限快照、同步渠道会话")
    void should_confirm_when_password_matches() {
      SecondaryAuthSession session = buildPendingSession();
      User approver = buildUser(UserStatus.ACTIVE);
      Credential credential = buildCredential();
      PermissionSnapshot snapshot = new PermissionSnapshot(
          UserId.of(APPROVER_ID), PLAN_ID,
          Set.of(PermissionCode.of("BIZ.HANDLE")),
          LocalDateTime.now());
      when(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE)))
          .thenReturn(Optional.of(session));
      when(userRepository.load(UserId.of(APPROVER_ID)))
          .thenReturn(Optional.of(approver));
      when(credentialRepository.findActive(APPROVER_ID, "BRANCH_TELLER", CredentialType.PASSWORD))
          .thenReturn(Optional.of(credential));
      when(passwordEncryptorPort.matches(RAW_PASSWORD, HASHED_PASSWORD))
          .thenReturn(true);
      when(permissionResolver.resolve(UserId.of(APPROVER_ID), PLAN_ID))
          .thenReturn(snapshot);
      ConfirmSecondaryAuthCommand command = new ConfirmSecondaryAuthCommand(
          SESSION_ID_VALUE, RAW_PASSWORD);

      SecondaryAuthSessionDTO dto = secondaryAuthAppService.confirm(command);

      assertThat(dto.status()).isEqualTo("AUTHORIZED");
      assertThat(dto.permissionSnapshot()).contains("BIZ.HANDLE");
      verify(sessionRepository).save(session);
      verify(channelSessionPort).setSecondaryAuthSession(
          eq(SESSION_ID_VALUE), eq(APPROVER_ID), eq(PLAN_ID), any());
    }

    @Test
    @DisplayName("会话不存在时抛业务异常,不进行密码校验")
    void should_throw_when_session_not_found() {
      when(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE)))
          .thenReturn(Optional.empty());
      ConfirmSecondaryAuthCommand command = new ConfirmSecondaryAuthCommand(
          SESSION_ID_VALUE, RAW_PASSWORD);

      assertThatThrownBy(() -> secondaryAuthAppService.confirm(command))
          .isInstanceOf(BusinessException.class);

      verify(passwordEncryptorPort, never()).matches(anyString(), anyString());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("密码不匹配时抛业务异常,不冻结权限快照")
    void should_throw_when_password_mismatch() {
      SecondaryAuthSession session = buildPendingSession();
      User approver = buildUser(UserStatus.ACTIVE);
      Credential credential = buildCredential();
      when(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE)))
          .thenReturn(Optional.of(session));
      when(userRepository.load(UserId.of(APPROVER_ID)))
          .thenReturn(Optional.of(approver));
      when(credentialRepository.findActive(APPROVER_ID, "BRANCH_TELLER", CredentialType.PASSWORD))
          .thenReturn(Optional.of(credential));
      when(passwordEncryptorPort.matches(RAW_PASSWORD, HASHED_PASSWORD))
          .thenReturn(false);
      ConfirmSecondaryAuthCommand command = new ConfirmSecondaryAuthCommand(
          SESSION_ID_VALUE, RAW_PASSWORD);

      assertThatThrownBy(() -> secondaryAuthAppService.confirm(command))
          .isInstanceOf(BusinessException.class);

      verify(permissionResolver, never()).resolve(any(), anyString());
      verify(sessionRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("revoke 撤销二次授权")
  class RevokeTest {

    @Test
    @DisplayName("撤销授权会话:状态转为 REVOKED 并清除渠道会话")
    void should_revoke_authorized_session() {
      SecondaryAuthSession session = buildAuthorizedSession();
      when(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE)))
          .thenReturn(Optional.of(session));
      when(channelSessionPort.currentUserId()).thenReturn(TELLER_ID);
      RevokeSecondaryAuthCommand command = new RevokeSecondaryAuthCommand(
          SESSION_ID_VALUE, REVOKE_REASON);

      secondaryAuthAppService.revoke(command);

      assertThat(session.status().name()).isEqualTo("REVOKED");
      verify(sessionRepository).save(session);
      verify(channelSessionPort).clearSecondaryAuthSession();
    }

    @Test
    @DisplayName("会话不存在时抛业务异常,不调用清除会话")
    void should_throw_when_session_not_found() {
      when(sessionRepository.load(SecondaryAuthSessionId.of(SESSION_ID_VALUE)))
          .thenReturn(Optional.empty());
      RevokeSecondaryAuthCommand command = new RevokeSecondaryAuthCommand(
          SESSION_ID_VALUE, REVOKE_REASON);

      assertThatThrownBy(() -> secondaryAuthAppService.revoke(command))
          .isInstanceOf(BusinessException.class);

      verify(channelSessionPort, never()).clearSecondaryAuthSession();
      verify(sessionRepository, never()).save(any());
    }
  }

  private User buildUser(UserStatus status) {
    return User.reconstitute(
        UserId.of(APPROVER_ID), ChannelType.BRANCH, APPROVER_LOGIN_NAME, "经办人",
        status, null, null, null,
        UserNo.of(OPERATOR), UserNo.of(OPERATOR),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private Credential buildCredential() {
    return Credential.reconstitute(
        com.example.iam.types.CredentialId.of(4001L),
        "BRANCH_TELLER", APPROVER_ID, CredentialType.PASSWORD,
        HASHED_PASSWORD, null, Map.of(),
        CredentialStatus.ACTIVE, null,
        UserNo.of(OPERATOR), UserNo.of(OPERATOR),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private SecondaryAuthSession buildPendingSession() {
    return SecondaryAuthSession.initiate(
        SecondaryAuthSessionId.of(SESSION_ID_VALUE),
        TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
        UserNo.of(OPERATOR));
  }

  private SecondaryAuthSession buildAuthorizedSession() {
    SecondaryAuthSession session = buildPendingSession();
    PermissionSnapshot snapshot = new PermissionSnapshot(
        UserId.of(APPROVER_ID), PLAN_ID,
        Set.of(PermissionCode.of("BIZ.HANDLE")),
        LocalDateTime.now());
    session.authorize(snapshot, LocalDateTime.now().plusHours(8), UserNo.of(OPERATOR));
    session.clearDomainEvents();
    return session;
  }
}
