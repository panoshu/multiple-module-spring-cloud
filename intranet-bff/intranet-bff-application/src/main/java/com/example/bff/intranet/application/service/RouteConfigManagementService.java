package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.shared.errorcode.BffErrorCode;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ApiResult<Long> create(BffRouteConfigRequest request) {
        Long id = routeConfigRepository.save(request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(id);
    }

    @Transactional
    public ApiResult<Void> update(Long id, BffRouteConfigRequest request) {
        requireExists(id);
        routeConfigRepository.update(id, request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    @Transactional
    public ApiResult<Void> delete(Long id) {
        requireExists(id);
        routeConfigRepository.delete(id, "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    public ApiResult<BffRouteConfigResponse> get(Long id) {
        BffRouteConfig config = routeConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BffErrorCode.ROUTE_NOT_FOUND)
                        .withUserDetail("未找到路由配置: " + id));
        return ApiResult.success(toResponse(id, config));
    }

    public ApiResult<List<BffRouteConfigResponse>> list() {
        List<BffRouteConfigResponse> list = routeConfigRepository.findAllWithId().stream()
                .map(entry -> toResponse(entry.id(), entry.config()))
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

    private void requireExists(Long id) {
        if (routeConfigRepository.findById(id).isEmpty()) {
            throw new BusinessException(BffErrorCode.ROUTE_NOT_FOUND)
                    .withUserDetail("未找到路由配置: " + id);
        }
    }
}
