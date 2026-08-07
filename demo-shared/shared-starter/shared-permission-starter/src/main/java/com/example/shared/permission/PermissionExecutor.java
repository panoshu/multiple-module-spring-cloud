package com.example.shared.permission;

/**
 * 权限校验执行器抽象接口.
 *
 * <p>业务服务通过 HttpExchange 调用 auth-service（{@code HttpExchangePermissionExecutor}），
 * auth-service 提供本地短路实现（{@code LocalPermissionExecutor}）避免循环调用。
 *
 * @author shared-permission-starter
 */
public interface PermissionExecutor {

    /**
     * 执行权限校验，返回是否允许。
     *
     * @param context 权限校验上下文
     * @return 校验结果
     */
    PermissionCheckResult check(PermissionCheckContext context);

    /**
     * 是否支持本地短路调用（auth-service 实现返回 true）。
     */
    default boolean isLocalExecution() {
        return false;
    }
}
