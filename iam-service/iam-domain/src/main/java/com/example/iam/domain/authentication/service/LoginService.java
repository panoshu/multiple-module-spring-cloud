package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.BranchUser;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.HqUser;
import com.example.iam.domain.authentication.aggregate.root.InternetUser;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.event.UserLoggedInEvent;
import com.example.iam.domain.authentication.hook.LoginHook;
import com.example.iam.domain.authentication.repository.BranchUserRepository;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.HqUserRepository;
import com.example.iam.domain.authentication.repository.InternetUserRepository;
import com.example.iam.domain.authentication.strategy.PasswordCredentialValidator;
import com.example.shared.domain.annotation.DomainService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 登录领域服务
 *
 * <p>统一处理三套渠道（INTERNET/HQ/BRANCH）的登录流程：
 * <ol>
 *   <li>触发 {@link LoginHook#preLogin} 钩子</li>
 *   <li>按渠道查找用户、校验状态、验证密码凭据</li>
 *   <li>成功时调用 {@code recordLogin}、保存聚合根</li>
 *   <li>失败时由 {@link LoginHook#postLoginFailure} 处理登录日志记录</li>
 *   <li>触发 {@link LoginHook#postLoginSuccess} 或 {@link LoginHook#postLoginFailure} 钩子</li>
 * </ol>
 *
 * <p>领域事件 {@link UserLoggedInEvent} 由应用层 Hook 实现负责发布，
 * 领域服务保持纯粹的业务逻辑编排。</p>
 *
 * <p><b>失败原因常量</b>（与 {@code IamAuthErrorCode} 的枚举名对应，应用层据此抛出业务异常）：
 * <ul>
 *   <li>{@code USER_NOT_FOUND} - 用户不存在</li>
 *   <li>{@code ACCOUNT_DISABLED} - 账号已禁用</li>
 *   <li>{@code ACCOUNT_LOCKED} - 账号已锁定</li>
 *   <li>{@code CREDENTIAL_INVALID} - 凭据不匹配</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DomainService
public class LoginService {

    /** 失败原因常量：用户不存在 */
    public static final String FAIL_USER_NOT_FOUND = "USER_NOT_FOUND";
    /** 失败原因常量：账号已禁用 */
    public static final String FAIL_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    /** 失败原因常量：账号已锁定 */
    public static final String FAIL_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    /** 失败原因常量：凭据不匹配 */
    public static final String FAIL_CREDENTIAL_INVALID = "CREDENTIAL_INVALID";

    private static final String INTERNET_USER_OWNER_TYPE = "INTERNET_USER";
    private static final String HQ_USER_OWNER_TYPE = "HQ_USER";
    private static final String BRANCH_USER_OWNER_TYPE = "BRANCH_USER";

    private final InternetUserRepository internetUserRepository;
    private final HqUserRepository hqUserRepository;
    private final BranchUserRepository branchUserRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordCredentialValidator passwordValidator;
    private final LoginHook loginHook;

    public LoginService(InternetUserRepository internetUserRepository,
                        HqUserRepository hqUserRepository,
                        BranchUserRepository branchUserRepository,
                        CredentialRepository credentialRepository,
                        PasswordCredentialValidator passwordValidator,
                        LoginHook loginHook) {
        this.internetUserRepository = internetUserRepository;
        this.hqUserRepository = hqUserRepository;
        this.branchUserRepository = branchUserRepository;
        this.credentialRepository = credentialRepository;
        this.passwordValidator = passwordValidator;
        this.loginHook = loginHook != null ? loginHook : LoginHook.NO_OP;
    }

    /**
     * 执行登录
     *
     * @param channel         渠道类型
     * @param loginName       登录名
     * @param credentialInput 凭据输入（明文密码）
     * @param ctx             登录上下文
     * @return 登录结果
     */
    public LoginResult login(ChannelType channel, String loginName, String credentialInput,
                             LoginHook.LoginContext ctx) {
        loginHook.preLogin(ctx);

        LoginResult result = switch (channel) {
            case INTERNET -> loginInternet(loginName, credentialInput, ctx);
            case HQ -> loginHq(loginName, credentialInput, ctx);
            case BRANCH -> loginBranch(loginName, credentialInput, ctx);
        };

        if (result.success()) {
            loginHook.postLoginSuccess(new LoginHook.LoginSuccessContext(
                result.userId(), channel, null, ctx.ipAddress(), ctx.userAgent()));
        } else {
            loginHook.postLoginFailure(new LoginHook.LoginFailureContext(
                loginName, channel, result.failReason(), ctx.ipAddress(), ctx.userAgent()));
        }
        return result;
    }

    private LoginResult loginInternet(String loginName, String password, LoginHook.LoginContext ctx) {
        Optional<InternetUser> userOpt = internetUserRepository.findByLoginName(loginName);
        if (userOpt.isEmpty()) {
            return LoginResult.failure(FAIL_USER_NOT_FOUND);
        }
        InternetUser user = userOpt.get();
        String statusFail = checkUserStatus(user.status());
        if (statusFail != null) {
            return LoginResult.failure(statusFail);
        }
        if (!verifyPassword(INTERNET_USER_OWNER_TYPE, user.id().value(), password)) {
            return LoginResult.failure(FAIL_CREDENTIAL_INVALID);
        }
        user.recordLogin(LocalDateTime.now(), ctx.ipAddress());
        internetUserRepository.save(user);
        return LoginResult.success(user.id().value(), ChannelType.INTERNET);
    }

    private LoginResult loginHq(String loginName, String password, LoginHook.LoginContext ctx) {
        Optional<HqUser> userOpt = hqUserRepository.findByLoginName(loginName);
        if (userOpt.isEmpty()) {
            return LoginResult.failure(FAIL_USER_NOT_FOUND);
        }
        HqUser user = userOpt.get();
        String statusFail = checkUserStatus(user.status());
        if (statusFail != null) {
            return LoginResult.failure(statusFail);
        }
        if (!verifyPassword(HQ_USER_OWNER_TYPE, user.id().value(), password)) {
            return LoginResult.failure(FAIL_CREDENTIAL_INVALID);
        }
        user.recordLogin(LocalDateTime.now(), ctx.ipAddress());
        hqUserRepository.save(user);
        return LoginResult.success(user.id().value(), ChannelType.HQ);
    }

    private LoginResult loginBranch(String loginName, String password, LoginHook.LoginContext ctx) {
        Optional<BranchUser> userOpt = branchUserRepository.findByLoginName(loginName);
        if (userOpt.isEmpty()) {
            return LoginResult.failure(FAIL_USER_NOT_FOUND);
        }
        BranchUser user = userOpt.get();
        String statusFail = checkUserStatus(user.status());
        if (statusFail != null) {
            return LoginResult.failure(statusFail);
        }
        if (!verifyPassword(BRANCH_USER_OWNER_TYPE, user.id().value(), password)) {
            return LoginResult.failure(FAIL_CREDENTIAL_INVALID);
        }
        user.recordLogin(LocalDateTime.now(), ctx.ipAddress());
        branchUserRepository.save(user);
        return LoginResult.success(user.id().value(), ChannelType.BRANCH);
    }

    /**
     * 校验账号状态，返回失败原因常量；状态正常返回 null
     */
    private String checkUserStatus(UserStatus status) {
        if (status == UserStatus.DISABLED) {
            return FAIL_ACCOUNT_DISABLED;
        }
        if (status == UserStatus.LOCKED) {
            return FAIL_ACCOUNT_LOCKED;
        }
        return null;
    }

    /**
     * 验证密码凭据
     */
    private boolean verifyPassword(String ownerType, Long ownerId, String password) {
        List<Credential> credentials = credentialRepository.findByOwnerAndType(
            ownerType, ownerId, CredentialType.PASSWORD);
        return credentials.stream()
            .anyMatch(c -> c.verify(password, passwordValidator));
    }

    /**
     * 登录结果值对象
     *
     * <p>使用静态工厂方法 {@link #success(Long, ChannelType)} 与 {@link #failure(String)} 构造</p>
     */
    public record LoginResult(boolean success, Long userId, ChannelType channel, String failReason) {

        public static LoginResult success(Long userId, ChannelType channel) {
            return new LoginResult(true, userId, channel, null);
        }

        public static LoginResult failure(String failReason) {
            return new LoginResult(false, null, null, failReason);
        }
    }
}
