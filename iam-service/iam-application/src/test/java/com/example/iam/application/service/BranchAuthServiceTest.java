package com.example.iam.application.service;

import com.example.iam.api.command.BranchLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
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

import java.util.List;
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
 * {@link BranchAuthService} 单元测试。
 *
 * <p>本测试同时覆盖父类 {@link AbstractChannelAuthService} 的通用登录/登出编排逻辑,
 * 包括用户不存在、账号状态异常、凭据缺失、密码错误与登录成功等场景。
 *
 * @author iam-service
 */
@DisplayName("网点渠道认证服务测试")
@ExtendWith(MockitoExtension.class)
class BranchAuthServiceTest {

    private static final Long TELLER_USER_ID = 1001L;
    private static final String TELLER_NO = "teller001";
    private static final String RAW_PASSWORD = "plain-pwd";
    private static final String HASHED_PASSWORD = "hashed-pwd";
    private static final String LOGIN_IP = "10.0.0.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String BRANCH_TELLER_OWNER_TYPE = "BRANCH_TELLER";

    @Mock private UserRepository userRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private LoginLogRepository loginLogRepository;
    @Mock private ChannelSessionPort channelSessionPort;
    @Mock private EventBus eventBus;
    @Mock private IdService idService;
    @Mock private PasswordEncryptorPort passwordEncryptorPort;
    @Mock private CredentialValidator credentialValidator;

    private BranchAuthService branchAuthService;

    @BeforeEach
    void setUpIdService() {
        // CredentialValidator SPI 支持 PASSWORD 类型,validate 行为由具体用例 mock
        lenient().when(credentialValidator.supports()).thenReturn(CredentialType.PASSWORD);
        // 手动构造服务,将 credentialValidator 包装为 List 注入(避免 @InjectMocks 无法注入集合)
        branchAuthService = new BranchAuthService(
            userRepository, credentialRepository, List.of(credentialValidator),
            loginLogRepository, channelSessionPort, eventBus, idService, passwordEncryptorPort);
        lenient().when(idService.nextLongId(LoginLogId.class, "IAM_LOGIN_LOG"))
            .thenReturn(LoginLogId.of(9001L));
        lenient().when(idService.nextLongId(LoginFailureRecordId.class, "IAM_LOGIN_FAIL"))
            .thenReturn(LoginFailureRecordId.of(9002L));
    }

    @Nested
    @DisplayName("login 登录流程")
    class LoginTest {

        @Test
        @DisplayName("用户不存在时返回失败结果并记录失败日志,不创建会话")
        void should_return_failure_when_user_not_found() {
            when(userRepository.findByLoginName(TELLER_NO, ChannelType.BRANCH))
                .thenReturn(Optional.empty());
            BranchLoginCommand command = new BranchLoginCommand(
                TELLER_NO, RAW_PASSWORD, LOGIN_IP, USER_AGENT);

            LoginResultDTO result = branchAuthService.login(command);

            assertThat(result.success()).isFalse();
            assertThat(result.channelType()).isEqualTo(ChannelType.BRANCH.name());
            assertThat(result.userId()).isNull();
            verify(loginLogRepository).save(any());
            verify(channelSessionPort, never()).login(anyLong(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("账号非活跃时返回失败结果并记录失败日志")
        void should_return_failure_when_user_not_active() {
            User disabledUser = buildUser(UserStatus.DISABLED);
            when(userRepository.findByLoginName(TELLER_NO, ChannelType.BRANCH))
                .thenReturn(Optional.of(disabledUser));
            BranchLoginCommand command = new BranchLoginCommand(
                TELLER_NO, RAW_PASSWORD, LOGIN_IP, USER_AGENT);

            LoginResultDTO result = branchAuthService.login(command);

            assertThat(result.success()).isFalse();
            verify(loginLogRepository).save(any());
            verify(channelSessionPort, never()).login(anyLong(), any());
            verify(credentialRepository, never())
                .findActive(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("密码凭据缺失时返回失败结果")
        void should_return_failure_when_credential_not_found() {
            User user = buildUser(UserStatus.ACTIVE);
            when(userRepository.findByLoginName(TELLER_NO, ChannelType.BRANCH))
                .thenReturn(Optional.of(user));
            when(credentialRepository.findActive(TELLER_USER_ID, BRANCH_TELLER_OWNER_TYPE, CredentialType.PASSWORD))
                .thenReturn(Optional.empty());
            BranchLoginCommand command = new BranchLoginCommand(
                TELLER_NO, RAW_PASSWORD, LOGIN_IP, USER_AGENT);

            LoginResultDTO result = branchAuthService.login(command);

            assertThat(result.success()).isFalse();
            verify(loginLogRepository).save(any());
            verify(passwordEncryptorPort, never()).matches(anyString(), anyString());
            verify(channelSessionPort, never()).login(anyLong(), any());
        }

        @Test
        @DisplayName("密码不匹配时返回失败结果")
        void should_return_failure_when_password_mismatch() {
            User user = buildUser(UserStatus.ACTIVE);
            Credential credential = buildCredential();
            when(userRepository.findByLoginName(TELLER_NO, ChannelType.BRANCH))
                .thenReturn(Optional.of(user));
            when(credentialRepository.findActive(TELLER_USER_ID, BRANCH_TELLER_OWNER_TYPE, CredentialType.PASSWORD))
                .thenReturn(Optional.of(credential));
            when(credentialValidator.validate(RAW_PASSWORD, credential))
                .thenReturn(false);
            BranchLoginCommand command = new BranchLoginCommand(
                TELLER_NO, RAW_PASSWORD, LOGIN_IP, USER_AGENT);

            LoginResultDTO result = branchAuthService.login(command);

            assertThat(result.success()).isFalse();
            verify(loginLogRepository).save(any());
            verify(userRepository, never()).save(any());
            verify(channelSessionPort, never()).login(anyLong(), any());
        }

        @Test
        @DisplayName("登录成功时保存用户、记录成功日志并创建渠道会话")
        void should_succeed_when_credential_matches() {
            User user = buildUser(UserStatus.ACTIVE);
            Credential credential = buildCredential();
            when(userRepository.findByLoginName(TELLER_NO, ChannelType.BRANCH))
                .thenReturn(Optional.of(user));
            when(credentialRepository.findActive(TELLER_USER_ID, BRANCH_TELLER_OWNER_TYPE, CredentialType.PASSWORD))
                .thenReturn(Optional.of(credential));
            when(credentialValidator.validate(RAW_PASSWORD, credential))
                .thenReturn(true);
            BranchLoginCommand command = new BranchLoginCommand(
                TELLER_NO, RAW_PASSWORD, LOGIN_IP, USER_AGENT);

            LoginResultDTO result = branchAuthService.login(command);

            assertThat(result.success()).isTrue();
            assertThat(result.userId()).isEqualTo(TELLER_USER_ID);
            assertThat(result.channelType()).isEqualTo(ChannelType.BRANCH.name());
            verify(userRepository).save(user);
            verify(loginLogRepository).save(any());
            verify(channelSessionPort).login(TELLER_USER_ID, ChannelType.BRANCH);
        }
    }

    @Nested
    @DisplayName("logout 登出流程")
    class LogoutTest {

        @Test
        @DisplayName("登出时清理二次授权会话并销毁渠道会话")
        void should_clear_secondary_session_and_logout_channel() {
            LogoutCommand command = new LogoutCommand("BRANCH");

            branchAuthService.logout(command);

            verify(channelSessionPort).clearSecondaryAuthSession();
            verify(channelSessionPort).logout(ChannelType.BRANCH);
        }
    }

    private User buildUser(UserStatus status) {
        return User.reconstitute(
            UserId.of(TELLER_USER_ID), ChannelType.BRANCH, TELLER_NO, "柜员",
            status, null, null, null,
            com.example.shared.primitives.identity.UserNo.of("admin"),
            com.example.shared.primitives.identity.UserNo.of("admin"),
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial());
    }

    private Credential buildCredential() {
        return Credential.reconstitute(
            com.example.iam.types.CredentialId.of(2001L),
            BRANCH_TELLER_OWNER_TYPE, TELLER_USER_ID, CredentialType.PASSWORD,
            HASHED_PASSWORD, null, java.util.Map.of(),
            com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus.ACTIVE, null,
            com.example.shared.primitives.identity.UserNo.of("admin"),
            com.example.shared.primitives.identity.UserNo.of("admin"),
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial());
    }
}
