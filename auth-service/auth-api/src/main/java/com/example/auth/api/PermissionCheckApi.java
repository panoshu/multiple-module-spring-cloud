package com.example.auth.api;

import com.example.shared.security.permission.DataScopeRequest;
import com.example.shared.security.permission.DataScopeResponse;
import com.example.shared.security.permission.PermissionCheckPort;
import com.example.shared.security.permission.PermissionCheckRequest;
import com.example.shared.security.permission.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限实时校验 API（内部接口，供业务服务通过 HttpExchange 调用）.
 *
 * <p>extends 纯契约 {@link PermissionCheckPort}：方法签名编译期一致，
 * 本接口补充传输层注解（@HttpExchange 路径从 declaring class 读取，不继承）。
 *
 * @author auth-api
 */
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi extends PermissionCheckPort {

  @Override
  @PostExchange("/check")
  ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);

  @PostExchange("/check-batch")
  ApiResult<com.example.auth.api.dto.PermissionCheckBatchResponse> checkBatch(
    @RequestBody @Valid com.example.auth.api.query.PermissionCheckBatchRequest request);

  /**
   * 解析数据可见范围（行级数据过滤用）.
   *
   * @param request 包含 accountId 和 businessCode
   * @return 可见 plans/customers 集合及 DENY 排除集合
   */
  @Override
  @PostExchange("/resolve-data-scope")
  ApiResult<DataScopeResponse> resolveDataScope(@RequestBody @Valid DataScopeRequest request);
}
