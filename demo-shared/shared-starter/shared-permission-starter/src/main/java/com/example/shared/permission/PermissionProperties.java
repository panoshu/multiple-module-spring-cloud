package com.example.shared.permission;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限校验配置项。
 *
 * <p>对应 application.yml 中的 {@code permission.*} 配置：
 * <pre>{@code
 * permission:
 *   session:
 *     signature-key: ${SESSION_SIGNATURE_KEY:}   # 网关与业务服务共享的 HMAC 密钥
 * }
 * }</pre>
 *
 * @author shared-permission-starter
 */
@Data
@ConfigurationProperties(prefix = "permission")
public class PermissionProperties {

  /**
   * 会话签名配置
   */
  private SessionConfig session = new SessionConfig();

  @Data
  public static class SessionConfig {
    /**
     * 网关与业务服务共享的 HMAC-SHA256 密钥。
     * <p>未配置时业务服务不验签，信任网关透传的 X-Account-Id
     */
    private String signatureKey = "";
  }
}
