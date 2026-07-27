package com.example.iam.adapter.security;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码类型凭据验证器 - {@link CredentialValidator} SPI 的实现。
 *
 * <p>使用 Spring Security 的 {@link BCryptPasswordEncoder} 完成密码哈希比对,
 * 盐值内嵌于哈希结果中,无需单独读取 {@link Credential#salt()} 字段。
 *
 * <p>通过 Spring {@code @Component} 自动注册到 {@code AbstractChannelAuthService}
 * 的 {@code List<CredentialValidator>} 集合中,在登录流程中根据
 * {@link Credential#credentialType()} 选择对应策略。
 *
 * <p>支持的凭据类型:{@link CredentialType#PASSWORD}。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class PasswordCredentialValidator implements CredentialValidator {

  /** BCrypt 加密强度(需与 {@link BcryptPasswordEncryptorAdapter} 保持一致) */
  private static final int STRENGTH = 10;

  private final BCryptPasswordEncoder passwordEncoder;

  public PasswordCredentialValidator() {
    this.passwordEncoder = new BCryptPasswordEncoder(STRENGTH);
  }

  @Override
  public CredentialType supports() {
    return CredentialType.PASSWORD;
  }

  @Override
  public boolean validate(String plainSecret, Credential credential) {
    if (plainSecret == null || plainSecret.isEmpty()) {
      return false;
    }
    String secretHash = credential.secretHash();
    if (secretHash == null || secretHash.isEmpty()) {
      return false;
    }
    try {
      return passwordEncoder.matches(plainSecret, secretHash);
    } catch (Exception e) {
      log.warn("密码校验失败(可能是密文格式异常), credentialId: {}",
          credential.id().value(), e);
      return false;
    }
  }
}
