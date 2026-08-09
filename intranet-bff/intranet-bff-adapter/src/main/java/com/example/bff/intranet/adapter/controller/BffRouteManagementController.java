package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.BffRouteManagementApi;
import com.example.bff.intranet.api.dto.BffRouteConfigDeleteRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigGetRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.intranet.api.dto.BffRouteConfigUpdateRequest;
import com.example.bff.intranet.application.service.RouteConfigManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 路由配置管理 Controller
 *
 * @author bff
 */
@RestController
public class BffRouteManagementController implements BffRouteManagementApi {

    private final RouteConfigManagementService routeConfigManagementService;

    public BffRouteManagementController(RouteConfigManagementService routeConfigManagementService) {
        this.routeConfigManagementService = routeConfigManagementService;
    }

    @Override
    public ApiResult<Long> create(BffRouteConfigRequest request) {
        return routeConfigManagementService.create(request);
    }

    @Override
    public ApiResult<Void> update(BffRouteConfigUpdateRequest request) {
        return routeConfigManagementService.update(request.id(), request.config());
    }

    @Override
    public ApiResult<Void> delete(BffRouteConfigDeleteRequest request) {
        return routeConfigManagementService.delete(request.id());
    }

    @Override
    public ApiResult<BffRouteConfigResponse> get(BffRouteConfigGetRequest request) {
        return routeConfigManagementService.get(request.id());
    }

    @Override
    public ApiResult<List<BffRouteConfigResponse>> list() {
        return routeConfigManagementService.list();
    }

    @Override
    public ApiResult<Void> refreshCache() {
        return routeConfigManagementService.refreshCache();
    }
}
