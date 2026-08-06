package com.pension.permission.domain.channel.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道错误码（channel 子模块，01xx 码段 0117 起）.
 *
 * <p>客户渠道开通相关的领域错误码，与 {@link SecondaryAuthErrorCode} 同属 channel 子模块。</p>
 */
@Getter
@AllArgsConstructor
public enum ChannelErrorCode implements ErrorDefinition {
  CUSTOMER_CHANNEL_NOT_ENABLED("SERVICE.AUTH.0117", "客户未开通该登录渠道");

  private final String code;
  private final String message;
}
