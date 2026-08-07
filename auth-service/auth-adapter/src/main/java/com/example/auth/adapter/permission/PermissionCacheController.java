package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.GetBusinessPermissionsRequest;
import com.example.auth.api.command.GetPlatformPermissionsRequest;
import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.adapter.converter.PermissionMetadataConverter;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionCacheService;
import com.pension.permission.domain.authorization.valueobject.Permission;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 权限快照查询 Controller.
 *
 * <p>实现 {@link PermissionCacheApi}，供前端展示可见性使用，不参与后端鉴权。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionCacheController implements PermissionCacheApi {

  private final PermissionCacheService cacheService;
  private final PermissionMetadataConverter converter;

  @Override
  @RequirePermission(business = "PERMISSION_CACHE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
    Set<Permission> permissions = cacheService.computePlatformPermissions(UserNo.of(request.accountId()));
    return ApiResult.success(converter.toPermissionResponseSet(permissions));
  }

  @Override
  @RequirePermission(business = "PERMISSION_CACHE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
    Set<Permission> permissions = cacheService.computeBusinessPermissions(
      UserNo.of(request.accountId()), PlanNo.of(request.planId()));
    return ApiResult.success(converter.toPermissionResponseSet(permissions));
  }
}
