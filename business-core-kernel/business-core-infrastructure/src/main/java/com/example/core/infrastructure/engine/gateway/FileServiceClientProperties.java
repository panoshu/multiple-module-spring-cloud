package com.example.core.infrastructure.engine.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * file-service 集成端点配置（2026-11-08 批次2）.
 *
 * <p>配置示例：{@code core.integration.file.base-url=http://127.0.0.1:18090}
 * ——未配置时 FileServiceIntegrationGateway 不装配（Mock 兜底）.</p>
 */
@Data
@ConfigurationProperties(prefix = "core.integration.file")
public class FileServiceClientProperties {

  /**
   * file-service 基础地址（装配开关：存在即启用真实 HTTP 链路）.
   */
  private String baseUrl;
}
