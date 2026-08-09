package com.example.auth.api;

import com.example.auth.api.command.PermissionRegistrationRequest;
import com.example.auth.api.dto.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限点上报 API（内部接口，供业务服务启动时上报 @RequirePermission 注解元数据）.
 *
 * @author auth-api
 */
@HttpExchange("/internal/permission-registration")
public interface PermissionRegistrationApi {

  /**
   * 批量上报权限点.
   *
   * @param request 包含来源服务名 + 权限点列表
   */
  @PostExchange("/register")
  ApiResult<PermissionRegistrationResponse> register(
    @RequestBody @Valid PermissionRegistrationRequest request);
}
