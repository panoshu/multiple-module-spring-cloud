package com.example.iam.domain.authentication.aggregate.valueobject;

/**
 * 凭据类型（开闭原则扩展点）
 *
 * <p>每种类型对应一个 {@code CredentialValidator} 策略实现：
 * <ul>
 *   <li>PASSWORD → PasswordCredentialValidator（BCrypt，默认实现）</li>
 *   <li>UKEY → UKeyCredentialValidator（未来扩展）</li>
 *   <li>OTP → OTPCredentialValidator（未来扩展）</li>
 *   <li>CERTIFICATE → 证书凭据验证器（未来扩展）</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum CredentialType {
  /** 密码凭据（BCrypt 哈希） */
  PASSWORD,
  /** UKey 凭据（USB Key 硬件证书） */
  UKEY,
  /** 一次性密码（短信/邮件验证码） */
  OTP,
  /** 数字证书 */
  CERTIFICATE
}
