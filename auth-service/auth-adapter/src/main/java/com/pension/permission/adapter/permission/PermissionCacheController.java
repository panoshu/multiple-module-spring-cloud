package com.pension.permission.adapter.permission;

import com.example.shared.web.core.api.ApiResult;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.api.PermissionCacheApi;
import com.pension.permission.api.dto.PermissionResponse;
import com.pension.permission.adapter.permission.converter.PermissionMetadataConverter;
import com.pension.permission.application.permission.PermissionCacheService;
import com.pension.permission.domain.authorization.valueobject.Permission;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@AllArgsConstructor
public class PermissionCacheController implements PermissionCacheApi {

  private final PermissionCacheService cacheService;
  private final PermissionMetadataConverter converter;

  @Override
  public ApiResult<Set<PermissionResponse>> getPlatformPermissions(String accountId) {
    Set<Permission> permissions = cacheService.computePlatformPermissions(UserNo.of(accountId));
    return ApiResult.success(converter.toPermissionResponseSet(permissions));
  }

  @Override
  public ApiResult<Set<PermissionResponse>> getBusinessPermissions(String accountId, String planId) {
    Set<Permission> permissions = cacheService.computeBusinessPermissions(
      UserNo.of(accountId), PlanNo.of(planId));
    return ApiResult.success(converter.toPermissionResponseSet(permissions));
  }
}
