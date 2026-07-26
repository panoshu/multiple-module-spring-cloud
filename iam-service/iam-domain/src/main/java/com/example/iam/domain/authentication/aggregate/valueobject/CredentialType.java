package com.example.iam.domain.authentication.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 凭据类型。
 *
 * <p>对应 {@code CredentialValidator} SPI 的 {@code supports()} 返回值,用于策略选择:
 * <ul>
 *   <li>{@link #PASSWORD} - 静态密码(默认凭据)</li>
 *   <li>{@link #UKEY} - USB Key 硬件证书(网点柜员)</li>
 *   <li>{@link #DYNAMIC_TOKEN} - 动态令牌(短信/TOTP)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum CredentialType implements ValueObject {
  PASSWORD,
  UKEY,
  DYNAMIC_TOKEN
}
