package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.BranchUser;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.HqUser;
import com.example.iam.domain.authentication.aggregate.root.InternetUser;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.hook.LoginHook;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.BranchUserRepository;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.HqUserRepository;
import com.example.iam.domain.authentication.repository.InternetUserRepository;
import com.example.iam.domain.authentication.strategy.PasswordCredentialValidator;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.CredentialId;
import com.example.iam.types.HqUserId;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginService} 领域服务单元测试
 *
 * <p>覆盖三套渠道（INTERNET/HQ/BRANCH）的登录成功/失败场景、状态校验、
 * 凭据验证、Hook 回调触发等核心行为</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DisplayName("LoginService 领域服务")
class LoginServiceTest {

    private InternetUserRepository internetUserRepository;
    private HqUserRepository hqUserRepository;
    private BranchUserRepository branchUserRepository;
    private CredentialRepository credentialRepository;
    private PasswordCredentialValidator passwordValidator;
    private LoginHook loginHook;
    private LoginService loginService;

    private static final String INTERNET_LOGIN_NAME = "hr001";
    private static final String INTERNET_PASSWORD = "Passw0rd!";
    private static final String INTERNET_WRONG_PASSWORD = "WrongPwd";
    private static final String IP = "127.0.0.1";
    private static final String UA = "JUnit/1.0";

    @BeforeEach
    void setUp() {
        internetUserRepository = mock(InternetUserRepository.class);
        hqUserRepository = mock(HqUserRepository.class);
        branchUserRepository = mock(BranchUserRepository.class);
        credentialRepository = mock(CredentialRepository.class);
        passwordValidator = new PasswordCredentialValidator();
        loginHook = mock(LoginHook.class);
        loginService = new LoginService(
            internetUserRepository, hqUserRepository, branchUserRepository,
            credentialRepository, passwordValidator, loginHook
        );
    }

    @Test
    @DisplayName("网上渠道用户凭据匹配：返回成功且触发 postLoginSuccess Hook")
    void login_should_return_success_when_internet_user_credential_matches() {
        InternetUser user = activeInternetUser(1L, "C001", INTERNET_LOGIN_NAME);
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.of(user));
        when(credentialRepository.findByOwnerAndType(eq("INTERNET_USER"), eq(1L), eq(CredentialType.PASSWORD)))
            .thenReturn(List.of(passwordCredential(10L, 1L, INTERNET_PASSWORD)));

        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(result.success()).isTrue();
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.channel()).isEqualTo(ChannelType.INTERNET);
        assertThat(result.failReason()).isNull();
        verify(internetUserRepository).save(user);
        verify(loginHook).preLogin(any());
        verify(loginHook).postLoginSuccess(any());
        verify(loginHook, never()).postLoginFailure(any());
    }

    @Test
    @DisplayName("用户不存在：返回 USER_NOT_FOUND 失败结果")
    void login_should_return_failure_when_user_not_found() {
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.empty());

        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).isEqualTo("USER_NOT_FOUND");
        verify(internetUserRepository, never()).save(any());
        verify(loginHook).postLoginFailure(any());
    }

    @Test
    @DisplayName("凭据不匹配：返回 CREDENTIAL_INVALID 失败结果")
    void login_should_return_failure_when_credential_mismatch() {
        InternetUser user = activeInternetUser(1L, "C001", INTERNET_LOGIN_NAME);
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.of(user));
        when(credentialRepository.findByOwnerAndType(eq("INTERNET_USER"), eq(1L), eq(CredentialType.PASSWORD)))
            .thenReturn(List.of(passwordCredential(10L, 1L, INTERNET_PASSWORD)));

        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_WRONG_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).isEqualTo("CREDENTIAL_INVALID");
        verify(internetUserRepository, never()).save(any());
        verify(loginHook).postLoginFailure(any());
    }

    @Test
    @DisplayName("账号已禁用：返回 ACCOUNT_DISABLED 失败结果")
    void login_should_return_failure_when_user_disabled() {
        InternetUser user = disabledInternetUser(1L, "C001", INTERNET_LOGIN_NAME);
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.of(user));

        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).isEqualTo("ACCOUNT_DISABLED");
        verify(credentialRepository, never()).findByOwnerAndType(any(), any(), any());
        verify(internetUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("账号已锁定：返回 ACCOUNT_LOCKED 失败结果")
    void login_should_return_failure_when_user_locked() {
        InternetUser user = lockedInternetUser(1L, "C001", INTERNET_LOGIN_NAME);
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.of(user));

        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(result.success()).isFalse();
        assertThat(result.failReason()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("总部渠道用户登录成功")
    void login_should_return_success_when_hq_user_credential_matches() {
        HqUser user = activeHqUser(2L, "admin001");
        when(hqUserRepository.findByLoginName("admin001"))
            .thenReturn(Optional.of(user));
        when(credentialRepository.findByOwnerAndType(eq("HQ_USER"), eq(2L), eq(CredentialType.PASSWORD)))
            .thenReturn(List.of(passwordCredential(20L, 2L, "AdminPwd!")));

        LoginService.LoginResult result = loginService.login(
            ChannelType.HQ, "admin001", "AdminPwd!", ctx("admin001", ChannelType.HQ));

        assertThat(result.success()).isTrue();
        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.channel()).isEqualTo(ChannelType.HQ);
        verify(hqUserRepository).save(user);
    }

    @Test
    @DisplayName("网点渠道用户登录成功")
    void login_should_return_success_when_branch_user_credential_matches() {
        BranchUser user = activeBranchUser(3L, "teller001");
        when(branchUserRepository.findByLoginName("teller001"))
            .thenReturn(Optional.of(user));
        when(credentialRepository.findByOwnerAndType(eq("BRANCH_USER"), eq(3L), eq(CredentialType.PASSWORD)))
            .thenReturn(List.of(passwordCredential(30L, 3L, "TellerPwd!")));

        LoginService.LoginResult result = loginService.login(
            ChannelType.BRANCH, "teller001", "TellerPwd!", ctx("teller001", ChannelType.BRANCH));

        assertThat(result.success()).isTrue();
        assertThat(result.userId()).isEqualTo(3L);
        assertThat(result.channel()).isEqualTo(ChannelType.BRANCH);
        verify(branchUserRepository).save(user);
    }

    @Test
    @DisplayName("登录成功后调用 recordLogin 记录登录时间与 IP")
    void login_should_call_recordLogin_on_success() {
        InternetUser user = activeInternetUser(1L, "C001", INTERNET_LOGIN_NAME);
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.of(user));
        when(credentialRepository.findByOwnerAndType(eq("INTERNET_USER"), eq(1L), eq(CredentialType.PASSWORD)))
            .thenReturn(List.of(passwordCredential(10L, 1L, INTERNET_PASSWORD)));

        loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        assertThat(user.lastLoginTime()).isNotNull();
        assertThat(user.lastLoginIp()).isEqualTo(IP);
    }

    @Test
    @DisplayName("preLogin Hook 总是被调用一次")
    void preLogin_hook_should_always_be_called() {
        when(internetUserRepository.findByLoginName(INTERNET_LOGIN_NAME))
            .thenReturn(Optional.empty());

        loginService.login(
            ChannelType.INTERNET, INTERNET_LOGIN_NAME, INTERNET_PASSWORD, ctx(INTERNET_LOGIN_NAME, ChannelType.INTERNET));

        verify(loginHook, times(1)).preLogin(any());
    }

    private LoginHook.LoginContext ctx(String loginName, ChannelType channel) {
        return new LoginHook.LoginContext(loginName, channel, IP, UA, Map.of());
    }

    private InternetUser activeInternetUser(Long id, String customerNo, String loginName) {
        return InternetUser.create(
            InternetUserId.of(id), CustomerNo.of(customerNo), loginName, "用户",
            UserNo.of("U-creator"));
    }

    private InternetUser disabledInternetUser(Long id, String customerNo, String loginName) {
        InternetUser user = activeInternetUser(id, customerNo, loginName);
        user.disable(UserNo.of("U-admin"));
        return user;
    }

    private InternetUser lockedInternetUser(Long id, String customerNo, String loginName) {
        InternetUser user = activeInternetUser(id, customerNo, loginName);
        user.lock(UserNo.of("U-admin"));
        return user;
    }

    private HqUser activeHqUser(Long id, String loginName) {
        return HqUser.create(
            HqUserId.of(id), "S001", loginName, "管理员", "IT",
            UserNo.of("U-creator"));
    }

    private BranchUser activeBranchUser(Long id, String loginName) {
        return BranchUser.create(
            BranchUserId.of(id), "B001", "BR001", "T001", loginName, "柜员",
            UserNo.of("U-creator"));
    }

    private Credential passwordCredential(Long credentialId, Long ownerId, String plainPassword) {
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        return Credential.reconstitute(
            CredentialId.of(credentialId),
            "INTERNET_USER", ownerId,
            CredentialType.PASSWORD, hash, null,
            UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U-creator"), UserNo.of("U-creator"),
            LocalDateTime.now(), LocalDateTime.now(),
            Version.initial()
        );
    }
}
