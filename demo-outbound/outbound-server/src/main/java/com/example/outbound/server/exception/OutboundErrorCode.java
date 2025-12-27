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
  ALIPAY_BIZ_ERROR("80100", "支付宝服务业务异常"),
  USER_NOT_EXIST("80110", "用户不存在"),
  BALANCE_NOT_ENOUGH("80120", "余额不足"),
  PAY_FAILED("80129", "Pay Failed"),

  SF_BIZ_ERROR("80200", "shunfeng.biz.error"),

  YT_BIZ_ERROR("80300", "yuantong.biz.error"),

  LOGISTICS_INFO_NOT_FOUND("80810", "Logistics Info Not Found"),
  STATUS_CANNOT_INTERCEPT("80811","物流拦截报错: {}"),
  LOGISTICS_QUERY_FAILED("80812", "logistics query failed"),
  PARAM_ERROR("80813", "物流发货报错: {}"),

  NOT_FOUND("80999", "信息未找到: {}"),



  ;

  private final String code;
  private final String message;
}
