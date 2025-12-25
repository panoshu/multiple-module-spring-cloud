package com.example.outbound.server.exception;

import com.example.shared.core.api.IResultCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GatewayErrorCode
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 14:16
 */

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum OutboundErrorCode implements IResultCode {
  LOGISTICS_INFO_NOT_FOUND("80010", "Logistics Info Not Found"),
  STATUS_CANNOT_INTERCEPT("80011","物流拦截报错: {}"),
  LOGISTICS_QUERY_FAILED("80012", "logistics query failed"),
  PARAM_ERROR("80020", "物流发货报错: {}"),
  NOT_FOUND("800404", "信息未找到: {}"),
  ALIPAY_BIZ_ERROR("80050", "支付宝服务业务异常"),
  USER_NOT_EXIST("80051", "用户不存在"),
  BALANCE_NOT_ENOUGH("80052", "余额不足"),
  PAY_FAILED("80053", "Pay Failed"),

  ;

  private final String code;
  private final String message;
}
