package com.example.integration.infrastructure.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * TradeErrorCode
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:47
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TradeErrorCode implements ErrorDefinition {

  TRADE_API_ERROR("99920", "Trade API Error"),
  TRADE_BALANCE_NOT_ENOUGH("80001", "账户余额不足，当前余额: {}"),
  TRADE_ACCOUNT_FROZEN("80002", "账户已被冻结"),
  TRADE_CHANNEL_CLOSED("80003", "交易渠道 [{}] 已关闭");

  private final String code;
  private final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
