package com.example.bff.intranet.adapter.controller;

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
import com.example.bff.intranet.api.BffPermissionApi;
import com.example.bff.intranet.application.service.PermissionManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 权限管理 Controller
 *
 * <p>透明转发到 auth-service，每个方法直接委托给 {@link PermissionManagementService}。
 *
 * @author bff
 */
@RestController
public class BffPermissionController implements BffPermissionApi {

    private final PermissionManagementService permissionManagementService;

    public BffPermissionController(PermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @Override
    public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return permissionManagementService.check(request);
    }

    @Override
    public ApiResult<PermissionCheckBatchResponse> checkBatch(PermissionCheckBatchRequest request) {
        return permissionManagementService.checkBatch(request);
    }

    @Override
    public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
        return permissionManagementService.resolveDataScope(request);
    }

    @Override
    public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
        return permissionManagementService.listItems(request);
    }

    @Override
    public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
        return permissionManagementService.listGroupedItems(request);
    }

    @Override
    public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
        return permissionManagementService.getPlatformPermissions(request);
    }

    @Override
    public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
        return permissionManagementService.getBusinessPermissions(request);
    }
}
