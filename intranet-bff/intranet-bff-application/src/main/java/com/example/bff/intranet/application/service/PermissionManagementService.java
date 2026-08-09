package com.example.bff.intranet.application.service;

import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.PermissionMetadataApi;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 权限管理服务
 *
 * <p>透明转发到 auth-service。三个 auth API（PermissionCheckApi/PermissionMetadataApi/PermissionCacheApi）
 * 由 httpexchange 客户端自动创建代理 Bean。
 *
 * @author bff
 */
@Service
public class PermissionManagementService {

    private final PermissionCheckApi permissionCheckApi;
    private final PermissionMetadataApi permissionMetadataApi;
    private final PermissionCacheApi permissionCacheApi;

    public PermissionManagementService(PermissionCheckApi permissionCheckApi,
                                      PermissionMetadataApi permissionMetadataApi,
                                      PermissionCacheApi permissionCacheApi) {
        this.permissionCheckApi = permissionCheckApi;
        this.permissionMetadataApi = permissionMetadataApi;
        this.permissionCacheApi = permissionCacheApi;
    }

    // ===== 权限校验（3 个） =====

    public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return permissionCheckApi.check(request);
    }

    public ApiResult<PermissionCheckBatchResponse> checkBatch(PermissionCheckBatchRequest request) {
        return permissionCheckApi.checkBatch(request);
    }

    public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
        return permissionCheckApi.resolveDataScope(request);
    }

    // ===== 权限元数据查询（2 个） =====

    public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
        return permissionMetadataApi.listItems(request);
    }

    public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
        return permissionMetadataApi.listGroupedItems(request);
    }

    // ===== 权限缓存查询（2 个） =====

    public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
        return permissionCacheApi.getPlatformPermissions(request);
    }

    public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
        return permissionCacheApi.getBusinessPermissions(request);
    }
}
