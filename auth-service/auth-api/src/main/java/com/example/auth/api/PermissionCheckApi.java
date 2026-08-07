package com.example.auth.api;

import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限实时校验 API（内部接口，供业务服务通过 HttpExchange 调用）.
 *
 * @author auth-api
 */
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {

    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@RequestBody @Valid PermissionCheckBatchRequest request);

    /**
     * 解析数据可见范围（行级数据过滤用）.
     *
     * @param request 包含 accountId 和 businessCode
     * @return 可见 plans/customers 集合及 DENY 排除集合
     */
    @PostExchange("/resolve-data-scope")
    ApiResult<DataScopeResponse> resolveDataScope(@RequestBody @Valid DataScopeRequest request);
}
