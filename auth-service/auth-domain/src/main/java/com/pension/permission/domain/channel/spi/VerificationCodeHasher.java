package com.pension.permission.domain.channel.spi;

/**
 * 验证码哈希端口.
 *
 * <p>domain 层不直接依赖 BCrypt 等加密库，通过此端口接口隔离。
 * 实现由 infrastructure 层提供（如 BCryptVerificationCodeHasher）。</p>
 */
public interface VerificationCodeHasher {

  /**
   * 对明文验证码进行哈希.
   *
   * @param rawCode 明文验证码
   * @return 哈希后的字符串
   */
  String hash(String rawCode);

  /**
   * 校验明文验证码是否匹配哈希值.
   *
   * @param rawCode    明文验证码
   * @param hashedCode 哈希后的字符串
   * @return 是否匹配
   */
  boolean matches(String rawCode, String hashedCode);
}
