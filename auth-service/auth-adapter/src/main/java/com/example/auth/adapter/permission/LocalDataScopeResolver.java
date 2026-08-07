package com.example.auth.adapter.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.permission.AccountIdResolver;
import com.example.shared.permission.DataScopeResolver;
import com.pension.permission.application.authorization.PermissionQueryService;
import com.pension.permission.application.authorization.ResolveDataScopeQuery;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * auth-service 本地数据范围解析器.
 *
 * <p>覆盖默认的 {@code DefaultDataScopeResolver}，避免 auth-service 调用自身触发循环依赖。
 * 直接调用 {@link PermissionQueryService#resolveDataScope} 解析可见范围。
 *
 * @author auth-adapter
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class LocalDataScopeResolver implements DataScopeResolver {

  private final PermissionQueryService permissionQueryService;
  private final AccountIdResolver accountIdResolver;

  @Override
  public DataScope resolve(String business) {
    String accountId = resolveCurrentAccountId();
    if (accountId == null || accountId.isBlank()) {
      return DataScope.empty();
    }
    try {
      ResolveDataScopeQuery query = new ResolveDataScopeQuery(
          UserNo.of(accountId), new BusinessCode(business));
      return permissionQueryService.resolveDataScope(query);
    } catch (Exception e) {
      log.warn("[LocalDataScopeResolver] 解析失败, fail-closed. account={}, business={}",
          accountId, business, e);
      return DataScope.empty();
    }
  }

  private String resolveCurrentAccountId() {
    return accountIdResolver.resolve(null);
  }
}
