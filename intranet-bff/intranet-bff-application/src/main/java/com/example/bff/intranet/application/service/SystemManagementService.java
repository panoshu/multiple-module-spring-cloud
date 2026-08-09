package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置管理服务
 *
 * <p>提供 BFF 运行时元信息查询和当前渠道支持的业务类型列表。
 * 系统信息从 {@link Environment} 读取，业务类型从 {@link BffRouteConfigRepository} 查询。
 *
 * @author bff
 */
@Service
public class SystemManagementService {

  private final Environment environment;
  private final BffRouteConfigRepository routeConfigRepository;

  public SystemManagementService(Environment environment,
                                 BffRouteConfigRepository routeConfigRepository) {
    this.environment = environment;
    this.routeConfigRepository = routeConfigRepository;
  }

  public ApiResult<BffSystemInfoResponse> getInfo() {
    BffSystemInfoResponse response = new BffSystemInfoResponse(
      environment.getProperty("bff.channel-scope", "ALL"),
      environment.getProperty("spring.application.name", "unknown"),
      environment.getProperty("server.port", Integer.class, 0),
      environment.getProperty("server.servlet.context-path", "/")
    );
    return ApiResult.success(response);
  }

  public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
    List<BffBusinessTypeResponse> list = routeConfigRepository.findAll().stream()
      .map(this::toResponse)
      .toList();
    return ApiResult.success(list);
  }

  private BffBusinessTypeResponse toResponse(BffRouteConfig config) {
    return new BffBusinessTypeResponse(
      config.businessType(),
      config.serviceName(),
      config.channelScope().name()
    );
  }
}
