package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionRegistrationApi;
import com.example.auth.api.command.PermissionRegistrationRequest;
import com.example.auth.api.dto.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.infrastructure.permission.PermissionRegistrationResult;
import com.pension.permission.infrastructure.permission.PermissionScannerService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点上报 Controller（接收业务服务上报）.
 *
 * <p>不标注 {@code @RequirePermission}：路径在 {@code /internal/**} 下，
 * 被网关 {@code ExcludeRouteFilter} 403 拦截，仅服务间调用可达。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionRegistrationController implements PermissionRegistrationApi {

  private final PermissionScannerService scannerService;

  @Override
  public ApiResult<PermissionRegistrationResponse> register(
    PermissionRegistrationRequest request) {
    PermissionRegistrationResult result = scannerService.registerFromExternal(
      request.sourceService(),
      request.items());
    return ApiResult.success(new PermissionRegistrationResponse(
      result.totalReceived(),
      result.upserted(),
      result.unchanged()));
  }
}
