package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.shared.business.ScenarioHandlerRegistry;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置管理服务
 *
 * <p>提供 BFF 运行时元信息查询和当前渠道支持的业务类型列表。
 *
 * @author bff
 */
@Service
public class SystemManagementService {

  private final Environment environment;
  private final ScenarioHandlerRegistry scenarioHandlerRegistry;

  public SystemManagementService(Environment environment,
                                 ScenarioHandlerRegistry scenarioHandlerRegistry) {
    this.environment = environment;
    this.scenarioHandlerRegistry = scenarioHandlerRegistry;
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

  /**
   * 查询当前渠道支持的业务类型列表。
   *
   * <p>由各 {@code BusinessScenarioHandler} 声明自身支持的业务类型，
   * 通过 {@link ScenarioHandlerRegistry} 汇总，避免维护第二份路由清单。
   */
  public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
    String channelScope = environment.getProperty("bff.channel-scope", "ALL");
    List<BffBusinessTypeResponse> types = scenarioHandlerRegistry.supportedTypes().stream()
      .sorted()
      .map(type -> new BffBusinessTypeResponse(
        type,
        scenarioHandlerRegistry.handlerFor(type).serviceName(),
        channelScope))
      .toList();
    return ApiResult.success(types);
  }
}
