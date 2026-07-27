package com.example.iam.adapter.security;

import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.domain.system.errorcode.IamSystemErrorCode;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码加密适配器 - 实现 {@link PasswordEncryptorPort} 端口。
 *
 * <p>使用 Spring Security 的 {@link BCryptPasswordEncoder} 完成密码哈希与校验,
 * 盐值内嵌于哈希结果中,无需单独维护盐字段。
 *
 * <p>加密强度默认为 10(rounds),可通过构造函数调整。加密失败抛出
 * {@link SystemException},避免泄露具体加密异常给上层。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class BcryptPasswordEncryptorAdapter implements PasswordEncryptorPort {

  /** BCrypt 加密强度(rounds,推荐值 10-12) */
  private static final int STRENGTH = 10;

  private final BCryptPasswordEncoder passwordEncoder;

  public BcryptPasswordEncryptorAdapter() {
    this.passwordEncoder = new BCryptPasswordEncoder(STRENGTH);
  }

  @Override
  public String encrypt(String plainPassword) {
    if (plainPassword == null || plainPassword.isEmpty()) {
      throw new SystemException(IamSystemErrorCode.CONFIG_INVALID)
          .withUserDetail("明文密码不能为空");
    }
    try {
      return passwordEncoder.encode(plainPassword);
    } catch (Exception e) {
      log.error("密码加密失败", e);
      throw new SystemException(IamSystemErrorCode.CONFIG_INVALID, e)
          .withUserDetail("密码加密失败");
    }
  }

  @Override
  public boolean matches(String plainPassword, String encryptedPassword) {
    if (plainPassword == null || plainPassword.isEmpty()) {
      return false;
    }
    if (encryptedPassword == null || encryptedPassword.isEmpty()) {
      return false;
    }
    try {
      return passwordEncoder.matches(plainPassword, encryptedPassword);
    } catch (Exception e) {
      log.warn("密码校验失败(可能是密文格式异常)", e);
      return false;
    }
  }
}
