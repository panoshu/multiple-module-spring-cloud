package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
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

  public SystemManagementService(Environment environment) {
    this.environment = environment;
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
   * <p>重构后由各 {@code BusinessScenarioHandler} 声明自身支持的业务类型，
   * 通过 {@code ScenarioHandlerRegistry} 汇总，而非从路由配置表查询。
   * 该方法将在 Task #5 完成 Strategy 基础设施后重新实现。
   */
  public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
    return ApiResult.success(List.of());
  }
}
