package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.dto.DataScope;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 业务服务默认数据范围解析器：通过 HttpExchange 调用 auth-service.
 *
 * <p>在 auth-service 中被 {@code LocalDataScopeResolver}（@Primary）覆盖。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionCheckApi.class)
@ConditionalOnMissingBean(DataScopeResolver.class)
public class DefaultDataScopeResolver implements DataScopeResolver {

    private final PermissionCheckApi permissionCheckApi;
    private final AccountIdResolver accountIdResolver;

    @Override
    public DataScope resolve(String business) {
        String accountId = resolveCurrentAccountId();
        if (accountId == null || accountId.isBlank()) {
            return DataScope.empty();
        }

        try {
            ApiResult<DataScopeResponse> result = permissionCheckApi.resolveDataScope(
                new DataScopeRequest(accountId, business));
            if (result == null || !result.isSuccess() || result.data() == null) {
                log.warn("[DefaultDataScopeResolver] auth-service 响应异常, fail-closed. account={}, business={}",
                    accountId, business);
                return DataScope.empty();
            }
            return toDataScope(result.data());
        } catch (Exception e) {
            log.warn("[DefaultDataScopeResolver] 调用 auth-service 失败, fail-closed. account={}, business={}",
                accountId, business, e);
            return DataScope.empty();
        }
    }

    private String resolveCurrentAccountId() {
        return accountIdResolver.resolve(null);
    }

    private DataScope toDataScope(DataScopeResponse resp) {
        Set<String> visiblePlans = new HashSet<>(resp.visiblePlans());
        Set<String> visibleCustomers = new HashSet<>(resp.visibleCustomers());
        visiblePlans.removeAll(resp.excludedPlans());
        visibleCustomers.removeAll(resp.excludedCustomers());
        return new DataScope(
            resp.globalVisible(),
            visiblePlans,
            visibleCustomers,
            resp.excludedPlans(),
            resp.excludedCustomers());
    }
}
