package com.example.shared.crypto.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shared-crypto 配置属性。
 *
 * <p>配置前缀：{@code shared.crypto}
 *
 * <pre>
 * shared:
 *   crypto:
 *     secret-key: ${CRYPTO_SECRET_KEY}   # Base64 编码的 SM4 密钥
 * </pre>
 *
 * @author trae
 * @since 1.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shared.crypto")
public class CryptoProperties {

  /** Base64 编码的 SM4 密钥（16 字节），建议通过环境变量注入 */
  private String secretKey;
}
