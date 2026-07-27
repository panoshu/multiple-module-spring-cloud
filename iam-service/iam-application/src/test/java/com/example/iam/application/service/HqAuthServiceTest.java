package com.example.iam.application.service;

import com.example.iam.api.command.HqLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HqAuthService} 单元测试。
 *
 * <p>覆盖总部渠道登录/登出流程,验证渠道类型(HQ)与归属类型(HQ_USER)的差异化声明。
 *
 * @author iam-service
 */
@DisplayName("总部渠道认证服务测试")
@ExtendWith(MockitoExtension.class)
class HqAuthServiceTest {

  private static final Long HQ_USER_ID = 2001L;
  private static final String EMPLOYEE_NO = "hq001";
  private static final String RAW_PASSWORD = "plain-pwd";
  private static final String HASHED_PASSWORD = "hashed-pwd";
  private static final String LOGIN_IP = "10.0.0.2";
  private static final String USER_AGENT = "Mozilla/5.0";
  private static final String HQ_USER_OWNER_TYPE = "HQ_USER";

  @Mock private UserRepository userRepository;
  @Mock private CredentialRepository credentialRepository;
  @Mock private LoginLogRepository loginLogRepository;
  @Mock private ChannelSessionPort channelSessionPort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;
  @Mock private PasswordEncryptorPort passwordEncryptorPort;
  @Mock private CredentialValidator credentialValidator;

  private HqAuthService hqAuthService;

  @BeforeEach
  void setUpIdService() {
    // CredentialValidator SPI 支持 PASSWORD 类型,validate 行为由具体用例 mock
    lenient().when(credentialValidator.supports()).thenReturn(CredentialType.PASSWORD);
    // 手动构造服务,将 credentialValidator 包装为 List 注入(避免 @InjectMocks 无法注入集合)
    hqAuthService = new HqAuthService(
        userRepository, credentialRepository, List.of(credentialValidator),
        loginLogRepository, channelSessionPort, eventBus, idService, passwordEncryptorPort);
    lenient().when(idService.nextLongId(LoginLogId.class, "IAM_LOGIN_LOG"))
        .thenReturn(LoginLogId.of(8001L));
    lenient().when(idService.nextLongId(LoginFailureRecordId.class, "IAM_LOGIN_FAIL"))
        .thenReturn(LoginFailureRecordId.of(8002L));
  }

  @Nested
  @DisplayName("login 登录流程")
  class LoginTest {

    @Test
    @DisplayName("用户不存在时返回失败结果并记录失败日志,不创建会话")
    void should_return_failure_when_user_not_found() {
      when(userRepository.findByLoginName(EMPLOYEE_NO, ChannelType.HQ))
          .thenReturn(Optional.empty());
      HqLoginCommand command = new HqLoginCommand(
          EMPLOYEE_NO, RAW_PASSWORD, null, LOGIN_IP, USER_AGENT);

      LoginResultDTO result = hqAuthService.login(command);

      assertThat(result.success()).isFalse();
      assertThat(result.channelType()).isEqualTo(ChannelType.HQ.name());
      assertThat(result.userId()).isNull();
      verify(loginLogRepository).save(any());
      verify(channelSessionPort, never()).login(anyLong(), any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("登录成功时保存用户、记录成功日志并创建总部渠道会话")
    void should_succeed_when_credential_matches() {
      User user = buildUser(UserStatus.ACTIVE);
      Credential credential = buildCredential();
      when(userRepository.findByLoginName(EMPLOYEE_NO, ChannelType.HQ))
          .thenReturn(Optional.of(user));
      when(credentialRepository.findActive(HQ_USER_ID, HQ_USER_OWNER_TYPE, CredentialType.PASSWORD))
          .thenReturn(Optional.of(credential));
      when(credentialValidator.validate(RAW_PASSWORD, credential))
          .thenReturn(true);
      HqLoginCommand command = new HqLoginCommand(
          EMPLOYEE_NO, RAW_PASSWORD, null, LOGIN_IP, USER_AGENT);

      LoginResultDTO result = hqAuthService.login(command);

      assertThat(result.success()).isTrue();
      assertThat(result.userId()).isEqualTo(HQ_USER_ID);
      assertThat(result.channelType()).isEqualTo(ChannelType.HQ.name());
      verify(userRepository).save(user);
      verify(loginLogRepository).save(any());
      verify(channelSessionPort).login(HQ_USER_ID, ChannelType.HQ);
    }
  }

  @Nested
  @DisplayName("logout 登出流程")
  class LogoutTest {

    @Test
    @DisplayName("登出时销毁总部渠道会话")
    void should_logout_hq_channel() {
      LogoutCommand command = new LogoutCommand("HQ");

      hqAuthService.logout(command);

      verify(channelSessionPort).logout(ChannelType.HQ);
    }
  }

  private User buildUser(UserStatus status) {
    return User.reconstitute(
        UserId.of(HQ_USER_ID), ChannelType.HQ, EMPLOYEE_NO, "总部员工",
        status, null, null, null,
        com.example.shared.primitives.identity.UserNo.of("admin"),
        com.example.shared.primitives.identity.UserNo.of("admin"),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private Credential buildCredential() {
    return Credential.reconstitute(
        com.example.iam.types.CredentialId.of(3001L),
        HQ_USER_OWNER_TYPE, HQ_USER_ID, CredentialType.PASSWORD,
        HASHED_PASSWORD, null, Map.of(),
        CredentialStatus.ACTIVE, null,
        com.example.shared.primitives.identity.UserNo.of("admin"),
        com.example.shared.primitives.identity.UserNo.of("admin"),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
