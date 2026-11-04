package com.example.shared.security.permission;

import com.example.shared.security.permission.DataScopeRequest;
import com.example.shared.security.permission.DataScopeResponse;
import com.example.shared.security.permission.PermissionCheckRequest;
import com.example.shared.security.permission.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;

/**
 * 权限实时校验端口（纯契约，无传输层注解）。
 *
 * <p>由 auth-service 实现服务端 Controller（@RequestMapping），客户端子接口
 * {@code PermissionCheckApi}（auth-api，@HttpExchange）extends 本端口并补充传输注解。
 * 消费方（shared-permission-starter 等）只依赖本端口，不依赖 auth-api。
 *
 * <p>方法签名与 {@code PermissionCheckApi} 完全一致（含 ApiResult 信封），
 * 以保证 extends 的编译期一致性。
 *
 * @author shared-security-contract
 */
public interface PermissionCheckPort {

  /**
   * 校验账号对指定权限点的访问权.
   *
   * @param request 包含 accountId、planId（业务权限）与权限码
   * @return allowed 判定
   */
  ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request);

  /**
   * 解析数据可见范围（行级数据过滤用）.
   *
   * @param request 包含 accountId 和 businessCode
   * @return 可见 plans/customers 集合及 DENY 排除集合
   */
  ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request);
}
