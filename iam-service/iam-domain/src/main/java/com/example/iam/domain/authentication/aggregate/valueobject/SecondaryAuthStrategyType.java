package com.example.iam.domain.authentication.aggregate.valueobject;

/**
 * 二次授权策略类型（开闭原则扩展点）
 *
 * <p>每种类型对应一个 {@code SecondaryAuthStrategy} 实现：
 * <ul>
 *   <li>CREDENTIAL → CredentialSecondaryAuthStrategy（凭据验证，默认实现）</li>
 *   <li>AUTHORIZATION_CODE → AuthorizationCodeSecondaryAuthStrategy（未来，授权码）</li>
 *   <li>SCAN → ScanSecondaryAuthStrategy（未来，扫码）</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum SecondaryAuthStrategyType {
  /** 凭据验证（验证经办人密码等） */
  CREDENTIAL,
  /** 授权码（经办人提供一次性授权码） */
  AUTHORIZATION_CODE,
  /** 扫码授权（经办人手机扫码确认） */
  SCAN
}
