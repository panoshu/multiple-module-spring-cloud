package com.example.iam.application.port;

/**
 * 密码加密端口 - 封装 BCrypt 等加密算法。
 *
 * <p>应用层通过此端口完成密码加密与校验,屏蔽具体加密实现。
 * iam-adapter 或 iam-infrastructure 层提供实现(基于 BCrypt 等算法)。
 *
 * <p>密码创建/修改时调用 {@link #encrypt} 加密明文密码;
 * 登录验证时调用 {@link #matches} 校验明文与密文是否匹配。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PasswordEncryptorPort {

  /**
   * 加密明文密码,返回密文。
   *
   * @param plainPassword 明文密码
   * @return 密文(含盐值或内嵌盐,如 BCrypt 哈希)
   */
  String encrypt(String plainPassword);

  /**
   * 校验明文与密文是否匹配。
   *
   * @param plainPassword    明文密码
   * @param encryptedPassword 密文
   * @return 匹配返回 true
   */
  boolean matches(String plainPassword, String encryptedPassword);
}
