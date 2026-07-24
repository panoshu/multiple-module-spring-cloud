package com.example.integration.infrastructure.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * integration-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code 32001-32099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 3 表示业务服务模块，2-3 位 20 表示 integration-service</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:47
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TradeErrorCode implements ErrorDefinition {

  TRADE_API_ERROR("32001", "交易接口调用异常"),
  TRADE_BALANCE_NOT_ENOUGH("32002", "账户余额不足"),
  TRADE_ACCOUNT_FROZEN("32003", "账户已被冻结"),
  TRADE_CHANNEL_CLOSED("32004", "交易渠道已关闭");

  private final String code;
  private final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
