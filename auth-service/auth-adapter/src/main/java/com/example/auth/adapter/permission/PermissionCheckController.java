package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckItemRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.DataScope;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckItemResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.authorization.CheckPermissionQuery;
import com.pension.permission.application.authorization.PermissionQueryService;
import com.pension.permission.application.authorization.ResolveDataScopeQuery;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限实时校验 Controller.
 *
 * <p>实现 {@link PermissionCheckApi}，委托 {@link PermissionQueryService} 做权限判定。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionCheckController implements PermissionCheckApi {

  private final PermissionQueryService permissionQueryService;

  @Override
  public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
    CheckPermissionQuery query = new CheckPermissionQuery(
        UserNo.of(request.accountId()),
        resolvePlanNo(request.planId()),
        new BusinessCode(request.businessCode()),
        resolveActionCode(request.actionCode()));
    boolean allowed = permissionQueryService.checkPermission(query);
    return ApiResult.success(new PermissionCheckResponse(allowed));
  }

  @Override
  public ApiResult<PermissionCheckBatchResponse> checkBatch(PermissionCheckBatchRequest request) {
    UserNo identity = UserNo.of(request.accountId());
    PlanNo planNo = resolvePlanNo(request.planId());

    List<PermissionCheckItemResponse> results = new ArrayList<>(request.items().size());
    for (PermissionCheckItemRequest item : request.items()) {
      CheckPermissionQuery query = new CheckPermissionQuery(
          identity,
          planNo,
          new BusinessCode(item.businessCode()),
          resolveActionCode(item.actionCode()));
      boolean allowed = permissionQueryService.checkPermission(query);
      results.add(new PermissionCheckItemResponse(
          item.businessCode(),
          item.actionCode(),
          allowed));
    }
    return ApiResult.success(new PermissionCheckBatchResponse(results));
  }

  @Override
  public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
    ResolveDataScopeQuery query = new ResolveDataScopeQuery(
        UserNo.of(request.accountId()),
        new BusinessCode(request.businessCode()));
    DataScope dataScope = permissionQueryService.resolveDataScope(query);
    return ApiResult.success(new DataScopeResponse(
        dataScope.globalVisible(),
        dataScope.visiblePlans(),
        dataScope.visibleCustomers(),
        dataScope.excludedPlans(),
        dataScope.excludedCustomers()));
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
