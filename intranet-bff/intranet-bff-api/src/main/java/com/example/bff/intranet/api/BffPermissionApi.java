package com.example.bff.intranet.api;

import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.api.query.DataScopeRequest;
import com.example.auth.api.query.GetBusinessPermissionsRequest;
import com.example.auth.api.query.GetPlatformPermissionsRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckBatchRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Set;

/**
 * 权限管理 BFF API
 *
 * <p>透明转发到 auth-service，合并权限校验、元数据查询、缓存查询三类接口。
 *
 * @author bff
 */
@HttpExchange("/management/permissions")
public interface BffPermissionApi {

    // ===== 权限校验（3 个） =====

    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@Valid @RequestBody PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@Valid @RequestBody PermissionCheckBatchRequest request);

    @PostExchange("/resolve-data-scope")
    ApiResult<DataScopeResponse> resolveDataScope(@Valid @RequestBody DataScopeRequest request);

    // ===== 权限元数据查询（2 个） =====

    @PostExchange("/metadata/items")
    ApiResult<List<PermissionItemResponse>> listItems(@Valid @RequestBody ListPermissionItemsRequest request);

    @PostExchange("/metadata/items/grouped")
    ApiResult<List<PermissionGroupResponse>> listGroupedItems(@Valid @RequestBody ListPermissionItemsRequest request);

    // ===== 权限缓存查询（2 个） =====

    @PostExchange("/cache/platform")
    ApiResult<Set<PermissionResponse>> getPlatformPermissions(@Valid @RequestBody GetPlatformPermissionsRequest request);

    @PostExchange("/cache/business")
    ApiResult<Set<PermissionResponse>> getBusinessPermissions(@Valid @RequestBody GetBusinessPermissionsRequest request);
}
