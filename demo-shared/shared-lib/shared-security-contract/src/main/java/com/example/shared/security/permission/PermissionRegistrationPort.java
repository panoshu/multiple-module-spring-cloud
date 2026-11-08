package com.example.shared.security.permission;

import com.example.shared.security.permission.PermissionRegistrationRequest;
import com.example.shared.security.permission.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;

/**
 * 权限点上报端口（纯契约，无传输层注解）。
 *
 * <p>由 auth-service 实现服务端 Controller；客户端子接口
 * {@code PermissionRegistrationApi}（auth-api，@HttpExchange）extends 本端口。
 * 消费方（shared-permission-starter 的 PermissionRegistrationRunner）只依赖本端口。
 *
 * @author shared-security-contract
 */
public interface PermissionRegistrationPort {

  /**
   * 批量上报权限点.
   *
   * @param request 包含来源服务名 + 权限点列表
   * @return 注册结果（受理数量等）
   */
  ApiResult<PermissionRegistrationResponse> register(PermissionRegistrationRequest request);
}
