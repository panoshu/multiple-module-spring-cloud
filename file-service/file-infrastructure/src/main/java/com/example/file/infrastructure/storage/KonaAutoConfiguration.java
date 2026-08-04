package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenGateway;
import com.example.shared.crypto.Sm4Encryptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.kona.crypto.KonaCryptoProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.Security;

/**
 * 文件令牌加解密自动配置。
 *
 * <p>注册 KonaCrypto Provider（兜底，若 shared-crypto-starter 已注册则跳过），
 * 构造 {@link Sm4Encryptor} 并注入 {@link KonaFileTokenGateway}。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.tencent.kona.crypto.KonaCryptoProvider")
@ConditionalOnProperty(prefix = "file.token", name = "secret-key")
@EnableConfigurationProperties(FileTokenProperties.class)
public class KonaAutoConfiguration {

  private final FileTokenProperties properties;

  /**
   * 兜底注册 KonaCrypto Provider。
   *
   * <p>若 shared-crypto-starter 已注册 Provider，则此处跳过。
   */
  @PostConstruct
  public void registerProvider() {
    if (Security.getProvider("KonaCrypto") == null) {
      Security.addProvider(new KonaCryptoProvider());
      log.info("KonaCrypto Provider 已注册（file-infrastructure 兜底）");
    }
  }

  @Bean
  @ConditionalOnMissingBean(FileTokenGateway.class)
  public FileTokenGateway fileTokenGateway(ObjectMapper objectMapper) {
    Sm4Encryptor encryptor = new Sm4Encryptor(properties.getSecretKey());
    return new KonaFileTokenGateway(objectMapper, encryptor);
  }
}
