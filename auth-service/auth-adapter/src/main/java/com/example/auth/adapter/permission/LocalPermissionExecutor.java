package com.example.auth.adapter.permission;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.permission.PermissionCheckContext;
import com.example.shared.permission.PermissionCheckResult;
import com.example.shared.permission.PermissionExecutor;
import com.pension.permission.application.authorization.CheckPermissionQuery;
import com.pension.permission.application.authorization.PermissionQueryService;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * auth-service 本地权限校验执行器.
 *
 * <p>覆盖默认的 {@code HttpExchangePermissionExecutor}，避免 auth-service 调用自身
 * 触发循环依赖。直接调用 {@link PermissionQueryService} 做权限判定。
 *
 * @author auth-adapter
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class LocalPermissionExecutor implements PermissionExecutor {

  private final PermissionQueryService permissionQueryService;

  @Override
  public PermissionCheckResult check(PermissionCheckContext context) {
    CheckPermissionQuery query = new CheckPermissionQuery(
      UserNo.of(context.accountId()),
      resolvePlanNo(context.planId()),
      new BusinessCode(context.businessCode()),
      resolveActionCode(context.actionCode()));
    boolean allowed = permissionQueryService.checkPermission(query);
    return allowed
      ? PermissionCheckResult.allow()
      : PermissionCheckResult.deny("权限不足");
  }

  @Override
  public boolean isLocalExecution() {
    return true;
  }

  private PlanNo resolvePlanNo(String planId) {
    if (planId == null || planId.isBlank()) {
      return null;
    }
    return PlanNo.of(planId);
  }

  private ActionCode resolveActionCode(String actionCode) {
    if (actionCode == null || actionCode.isBlank()) {
      return null;
    }
    return new ActionCode(actionCode);
  }
}
