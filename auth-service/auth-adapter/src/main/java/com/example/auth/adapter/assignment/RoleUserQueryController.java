package com.example.auth.adapter.assignment;

import com.example.auth.api.RoleUserQueryApi;
import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.auth.application.assignment.RoleUserQueryService;
import com.example.shared.security.annotation.PermissionCategory;
import com.example.shared.security.annotation.RequirePermission;
import com.example.shared.web.core.api.ApiResult;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 「角色→用户」查询 Controller（批次 59）。
 * <p>
 * 实现 {@link RoleUserQueryApi}，委托 {@link RoleUserQueryService} 分组后包装响应；
 * 响应只含用户编号，不含敏感字段。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class RoleUserQueryController implements RoleUserQueryApi {

  private final RoleUserQueryService roleUserQueryService;

  @Override
  @RequirePermission(business = "AUTH_ASSIGNMENT", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<RoleUserBatchResponse> listByRoles(ListRoleUsersRequest request) {
    Map<String, List<String>> usersByRole =
      roleUserQueryService.listByRoles(request.roleCodes(), request.planNo());
    return ApiResult.success(new RoleUserBatchResponse(usersByRole));
  }
}
