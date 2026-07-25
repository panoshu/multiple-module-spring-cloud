package com.example.iam.domain.authentication.hook;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;

import java.util.Map;

/**
 * 登录钩子接口（开闭原则扩展点）
 *
 * <p>应用层可提供自定义实现，在登录前后执行额外逻辑（如登录失败计数、IP 黑名单校验等）。
 * 默认实现为 {@link #NO_OP}，所有方法均为空操作。</p>
 */
public interface LoginHook {

    /** 空操作默认实现 */
    LoginHook NO_OP = new LoginHook() {};

    /**
     * 登录前钩子（可用于 IP 黑名单校验、参数预处理等）
     */
    default void preLogin(LoginContext ctx) {}

    /**
     * 登录成功后钩子（可用于发送通知、记录指标等）
     */
    default void postLoginSuccess(LoginSuccessContext ctx) {}

    /**
     * 登录失败后钩子（可用于失败计数、账号锁定等）
     */
    default void postLoginFailure(LoginFailureContext ctx) {}

    /**
     * 登录上下文
     */
    record LoginContext(String loginName, ChannelType channel, String ipAddress, String userAgent,
                        Map<String, Object> attributes) {}

    /**
     * 登录成功上下文
     */
    record LoginSuccessContext(Long userId, ChannelType channel, String tokenValue,
                               String ipAddress, String userAgent) {}

    /**
     * 登录失败上下文
     */
    record LoginFailureContext(String loginName, ChannelType channel, String failReason,
                               String ipAddress, String userAgent) {}
}
