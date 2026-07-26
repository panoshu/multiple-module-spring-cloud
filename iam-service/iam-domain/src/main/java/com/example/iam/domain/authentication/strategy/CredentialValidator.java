package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;

/**
 * 凭据验证策略 SPI。
 *
 * <p>不同凭据类型(密码/UKey/动态令牌)使用不同的验证算法:
 * <ul>
 *   <li>{@link CredentialType#PASSWORD} - BCrypt 哈希比对</li>
 *   <li>{@link CredentialType#UKEY} - RSA 公钥签名验证</li>
 *   <li>{@link CredentialType#DYNAMIC_TOKEN} - TOTP 算法</li>
 * </ul>
 *
 * <p>实现类位于 {@code iam-infrastructure} 层,通过 Spring {@code @Component} 自动注册。
 * 应用层在调用 {@link Credential#verify(String, CredentialValidator)} 时,根据
 * {@link Credential#credentialType()} 选择对应的策略 Bean。
 *
 * <p>本接口属于 {@code domain.strategy} 包,作为领域扩展点(SPI),不依赖任何外部框架。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface CredentialValidator {

  /**
   * 本策略支持的凭据类型。
   *
   * @return 凭据类型
   */
  CredentialType supports();

  /**
   * 验证凭据。
   *
   * @param plainSecret 用户提交的明文凭据(明文密码/UKEY 签名值/动态令牌)
   * @param credential  凭据聚合根(提供 secretHash/salt/auxData 等元数据)
   * @return 验证通过返回 true,失败返回 false
   */
  boolean validate(String plainSecret, Credential credential);
}
