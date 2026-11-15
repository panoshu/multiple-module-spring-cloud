package com.example.auth.adapter.assignment;

import com.example.auth.api.PlanDirectoryApi;
import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.auth.application.assignment.PlanDirectoryQueryService;
import com.example.shared.security.annotation.PermissionCategory;
import com.example.shared.security.annotation.RequirePermission;
import com.example.shared.web.core.api.ApiResult;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织目录查询 Controller（立项批次 67）。
 * <p>
 * 实现 {@link PlanDirectoryApi}，委托 {@link PlanDirectoryQueryService} 后包装响应；
 * 计划成员属敏感数据，按 {@code @RequirePermission} 保护，响应只含用户编号。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PlanDirectoryController implements PlanDirectoryApi {

  private final PlanDirectoryQueryService planDirectoryQueryService;

  @Override
  @RequirePermission(business = "AUTH_ASSIGNMENT", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<PlanMembersResponse> listMembers(ListPlanMembersRequest request) {
    return ApiResult.success(new PlanMembersResponse(
      planDirectoryQueryService.listMembers(request.planNo(), request.excludeUserNo())));
  }

  @Override
  @RequirePermission(business = "AUTH_ASSIGNMENT", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<ParentPlanResponse> getParentPlan(GetParentPlanRequest request) {
    return ApiResult.success(new ParentPlanResponse(
      planDirectoryQueryService.getParentPlan(request.planNo())));
  }
}
