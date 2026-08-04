package com.example.gateway.config;

import com.example.gateway.crypto.CryptoPolicy;
import com.example.gateway.crypto.Sm4CryptoPolicy;
import com.example.shared.crypto.Encryptor;
import com.example.shared.crypto.Sm4Encryptor;
import com.example.shared.json.matcher.SimpleFieldMatcher;
import com.example.shared.json.processor.JsonFieldProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 报文加解密 Bean 装配。
 *
 * <p>装配职责划分（SRP）：
 * <ul>
 *   <li>{@link Encryptor} — 具体加解密算法（SM4 / Noop）</li>
 *   <li>{@link JsonFieldProcessor} — JSON 流式遍历 + 字段匹配（基于 {@link SimpleFieldMatcher}）</li>
 *   <li>{@link CryptoPolicy} — 加解密策略，提供加密/解密 {@link com.example.shared.json.action.FieldAction}</li>
 * </ul>
 *
 * <p>当 gateway.crypto.enabled=true 时装配 Sm4Encryptor，否则使用 NoopEncryptor 占位。
 * CryptoFilter 在 enabled=false 时不会调用 processor 和 policy。
 *
 * @author trae
 * @since 1.0
 */
@Configuration
public class CryptoConfiguration {

  private static Set<String> collectFieldNames(CryptoProperties properties) {
    Set<String> names = new HashSet<>();
    for (Map.Entry<String, CryptoProperties.FieldConfig> entry : properties.fields().entrySet()) {
      names.add(entry.getKey());
      names.addAll(entry.getValue().aliases());
    }
    return names;
  }

  @Bean
  @ConditionalOnProperty(prefix = "gateway.crypto", name = "enabled", havingValue = "true")
  public Encryptor sm4Encryptor(CryptoProperties properties) {
    String secretKey = properties.secretKey();
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
        "gateway.crypto.secret-key must be configured when gateway.crypto.enabled=true");
    }
    return new Sm4Encryptor(secretKey);
  }

  @Bean
  @ConditionalOnMissingBean(Encryptor.class)
  public Encryptor noopEncryptor() {
    return new NoopEncryptor();
  }

  @Bean
  public JsonFieldProcessor jsonFieldProcessor(CryptoProperties properties) {
    Set<String> fieldNames = collectFieldNames(properties);
    SimpleFieldMatcher matcher = new SimpleFieldMatcher(fieldNames);
    return new JsonFieldProcessor(matcher);
  }

  @Bean
  public CryptoPolicy cryptoPolicy(Encryptor encryptor) {
    return new Sm4CryptoPolicy(encryptor);
  }

  /**
   * 占位加密器，enabled=false 时使用，方法不会被实际调用。
   */
  static final class NoopEncryptor implements Encryptor {
    @Override
    public String encrypt(String plaintext) {
      return plaintext;
    }

    @Override
    public String decrypt(String ciphertext) {
      return ciphertext;
    }
  }
}
