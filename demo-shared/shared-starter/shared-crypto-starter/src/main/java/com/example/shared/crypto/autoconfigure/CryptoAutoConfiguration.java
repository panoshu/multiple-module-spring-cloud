package com.example.shared.crypto.autoconfigure;

import com.example.shared.crypto.Encryptor;
import com.example.shared.crypto.Sm4Encryptor;
import com.example.shared.crypto.errorcode.CryptoErrorCode;
import com.example.shared.exception.SystemException;
import com.tencent.kona.crypto.KonaCryptoProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.Security;

/**
 * shared-crypto 自动配置类。
 *
 * <p>当配置 {@code shared.crypto.secret-key} 存在时，自动注册 {@link Sm4Encryptor} Bean。
 * 同时自动注册 KonaCrypto Provider（国密算法提供者）。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "shared.crypto", name = "secret-key")
@ConditionalOnClass(name = "com.tencent.kona.crypto.KonaCryptoProvider")
public class CryptoAutoConfiguration {

  /**
   * 注册 KonaCrypto Provider（国密算法提供者）。
   *
   * <p>Provider 是 JVM 全局的，只需注册一次。若已存在则跳过。
   */
  @PostConstruct
  public void registerProvider() {
    if (Security.getProvider("KonaCrypto") == null) {
      Security.addProvider(new KonaCryptoProvider());
      log.info("KonaCrypto Provider 已注册");
    } else {
      log.debug("KonaCrypto Provider 已存在，跳过注册");
    }
  }

  /**
   * 注册 SM4 加解密器 Bean。
   *
   * @param properties 加密配置属性
   * @return Sm4Encryptor 实例
   */
  @Bean
  @ConditionalOnMissingBean(Encryptor.class)
  public Encryptor sm4Encryptor(CryptoProperties properties) {
    String secretKey = properties.getSecretKey();
    if (secretKey == null || secretKey.isBlank()) {
      throw new SystemException(CryptoErrorCode.SECRET_KEY_NOT_CONFIGURED)
        .withLogDetail("shared.crypto.secret-key 未配置");
    }
    log.info("初始化 Sm4Encryptor（国密 SM4 加解密器）");
    return new Sm4Encryptor(secretKey);
  }
}
