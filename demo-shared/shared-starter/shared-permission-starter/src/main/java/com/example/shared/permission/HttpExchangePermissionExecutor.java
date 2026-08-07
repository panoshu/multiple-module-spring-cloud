package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 业务服务默认权限校验执行器：通过 HttpExchange 调用 auth-service.
 *
 * <p>在 auth-service 中被 {@code LocalPermissionExecutor}（@Primary）覆盖。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionCheckApi.class)
@ConditionalOnMissingBean(PermissionExecutor.class)
public class HttpExchangePermissionExecutor implements PermissionExecutor {

    private final PermissionCheckApi permissionCheckApi;

    @Override
    public PermissionCheckResult check(PermissionCheckContext context) {
        PermissionCheckRequest request = new PermissionCheckRequest(
            context.accountId(),
            context.planId(),
            context.businessCode(),
            context.actionCode());
        ApiResult<PermissionCheckResponse> result = permissionCheckApi.check(request);

        if (result == null || !result.isSuccess() || result.data() == null) {
            log.warn("[HttpExchangePermissionExecutor] auth-service 响应异常: result={}", result);
            return PermissionCheckResult.deny("auth-service 响应异常");
        }
        if (result.data().allowed()) {
            return PermissionCheckResult.allow();
        }
        return PermissionCheckResult.deny("权限不足");
    }

    @Override
    public boolean isLocalExecution() {
        return false;
    }
}
