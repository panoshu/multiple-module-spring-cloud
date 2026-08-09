package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 路由配置管理服务
 *
 * <p>编排路由配置的 CRUD 操作，刷新缓存委托给 {@link BusinessTypeRouter}。
 *
 * @author bff
 */
@Service
public class RouteConfigManagementService {

    private final BffRouteConfigRepository routeConfigRepository;
    private final BusinessTypeRouter businessTypeRouter;

    public RouteConfigManagementService(BffRouteConfigRepository routeConfigRepository,
                                       BusinessTypeRouter businessTypeRouter) {
        this.routeConfigRepository = routeConfigRepository;
        this.businessTypeRouter = businessTypeRouter;
    }

    public ApiResult<Long> create(BffRouteConfigRequest request) {
        Long id = routeConfigRepository.save(request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(id);
    }

    public ApiResult<Void> update(Long id, BffRouteConfigRequest request) {
        routeConfigRepository.update(id, request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    public ApiResult<Void> delete(Long id) {
        routeConfigRepository.delete(id, "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    public ApiResult<BffRouteConfigResponse> get(Long id) {
        return routeConfigRepository.findById(id)
                .map(config -> ApiResult.success(toResponse(id, config)))
                .orElseGet(() -> ApiResult.success(null));
    }

    public ApiResult<List<BffRouteConfigResponse>> list() {
        List<BffRouteConfigResponse> list = routeConfigRepository.findAll().stream()
                .map(this::toResponseWithGeneratedId)
                .toList();
        return ApiResult.success(list);
    }

    public ApiResult<Void> refreshCache() {
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    private BffRouteConfigResponse toResponse(Long id, BffRouteConfig config) {
        return new BffRouteConfigResponse(id, config.businessType(), config.serviceName(), config.channelScope());
    }

    private BffRouteConfigResponse toResponseWithGeneratedId(BffRouteConfig config) {
        // findAll 不返回 ID（Repository 接口的 findAll 返回 BffRouteConfig 不含 ID），此处 ID 设为 null
        return new BffRouteConfigResponse(null, config.businessType(), config.serviceName(), config.channelScope());
    }
}
