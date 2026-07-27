package com.example.iam.application.service;

import com.example.iam.api.command.InternetLoginCommand;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InternetAuthService} 单元测试。
 *
 * <p>覆盖网上渠道登录/登出流程,验证渠道类型(INTERNET)与归属类型(INTERNET_USER)的差异化声明。
 *
 * @author iam-service
 */
@DisplayName("网上渠道认证服务测试")
@ExtendWith(MockitoExtension.class)
class InternetAuthServiceTest {

  private static final Long INTERNET_USER_ID = 3001L;
  private static final String LOGIN_NAME = "user001";
  private static final String RAW_PASSWORD = "plain-pwd";
  private static final String HASHED_PASSWORD = "hashed-pwd";
  private static final String LOGIN_IP = "192.168.1.1";
  private static final String USER_AGENT = "Mozilla/5.0";
  private static final String INTERNET_USER_OWNER_TYPE = "INTERNET_USER";

  @Mock private UserRepository userRepository;
  @Mock private CredentialRepository credentialRepository;
  @Mock private LoginLogRepository loginLogRepository;
  @Mock private ChannelSessionPort channelSessionPort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;
  @Mock private PasswordEncryptorPort passwordEncryptorPort;
  @Mock private CredentialValidator credentialValidator;

  private InternetAuthService internetAuthService;

  @BeforeEach
  void setUpIdService() {
    // CredentialValidator SPI 支持 PASSWORD 类型,validate 行为由具体用例 mock
    lenient().when(credentialValidator.supports()).thenReturn(CredentialType.PASSWORD);
    // 手动构造服务,将 credentialValidator 包装为 List 注入(避免 @InjectMocks 无法注入集合)
    internetAuthService = new InternetAuthService(
        userRepository, credentialRepository, java.util.List.of(credentialValidator),
        loginLogRepository, channelSessionPort, eventBus, idService, passwordEncryptorPort);
    lenient().when(idService.nextLongId(LoginLogId.class, "IAM_LOGIN_LOG"))
        .thenReturn(LoginLogId.of(7001L));
    lenient().when(idService.nextLongId(LoginFailureRecordId.class, "IAM_LOGIN_FAIL"))
        .thenReturn(LoginFailureRecordId.of(7002L));
  }

  @Nested
  @DisplayName("login 登录流程")
  class LoginTest {

    @Test
    @DisplayName("用户不存在时返回失败结果并记录失败日志,不创建会话")
    void should_return_failure_when_user_not_found() {
      when(userRepository.findByLoginName(LOGIN_NAME, ChannelType.INTERNET))
          .thenReturn(Optional.empty());
      InternetLoginCommand command = new InternetLoginCommand(
          LOGIN_NAME, RAW_PASSWORD, null, LOGIN_IP, USER_AGENT);

      LoginResultDTO result = internetAuthService.login(command);

      assertThat(result.success()).isFalse();
      assertThat(result.channelType()).isEqualTo(ChannelType.INTERNET.name());
      assertThat(result.userId()).isNull();
      verify(loginLogRepository).save(any());
      verify(channelSessionPort, never()).login(anyLong(), any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("登录成功时保存用户、记录成功日志并创建网上渠道会话")
    void should_succeed_when_credential_matches() {
      User user = buildUser(UserStatus.ACTIVE);
      Credential credential = buildCredential();
      when(userRepository.findByLoginName(LOGIN_NAME, ChannelType.INTERNET))
          .thenReturn(Optional.of(user));
      when(credentialRepository.findActive(INTERNET_USER_ID, INTERNET_USER_OWNER_TYPE, CredentialType.PASSWORD))
          .thenReturn(Optional.of(credential));
      when(credentialValidator.validate(RAW_PASSWORD, credential))
          .thenReturn(true);
      InternetLoginCommand command = new InternetLoginCommand(
          LOGIN_NAME, RAW_PASSWORD, null, LOGIN_IP, USER_AGENT);

      LoginResultDTO result = internetAuthService.login(command);

      assertThat(result.success()).isTrue();
      assertThat(result.userId()).isEqualTo(INTERNET_USER_ID);
      assertThat(result.channelType()).isEqualTo(ChannelType.INTERNET.name());
      verify(userRepository).save(user);
      verify(loginLogRepository).save(any());
      verify(channelSessionPort).login(INTERNET_USER_ID, ChannelType.INTERNET);
    }
  }

  @Nested
  @DisplayName("logout 登出流程")
  class LogoutTest {

    @Test
    @DisplayName("登出时销毁网上渠道会话")
    void should_logout_internet_channel() {
      LogoutCommand command = new LogoutCommand("INTERNET");

      internetAuthService.logout(command);

      verify(channelSessionPort).logout(ChannelType.INTERNET);
    }
  }

  private User buildUser(UserStatus status) {
    return User.reconstitute(
        UserId.of(INTERNET_USER_ID), ChannelType.INTERNET, LOGIN_NAME, "网上用户",
        status, null, null, null,
        com.example.shared.primitives.identity.UserNo.of("admin"),
        com.example.shared.primitives.identity.UserNo.of("admin"),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }

  private Credential buildCredential() {
    return Credential.reconstitute(
        com.example.iam.types.CredentialId.of(4001L),
        INTERNET_USER_OWNER_TYPE, INTERNET_USER_ID, CredentialType.PASSWORD,
        HASHED_PASSWORD, null, Map.of(),
        CredentialStatus.ACTIVE, null,
        com.example.shared.primitives.identity.UserNo.of("admin"),
        com.example.shared.primitives.identity.UserNo.of("admin"),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
