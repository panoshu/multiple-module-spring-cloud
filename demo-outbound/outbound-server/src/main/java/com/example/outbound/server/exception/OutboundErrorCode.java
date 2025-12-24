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

  EXTERNAL_SERVICE_ERROR("89999", "外部调用错误: {}"),
  STATUS_CANNOT_INTERCEPT("80010","物流拦截报错: {}"),
  PARAM_ERROR("80020", "物流发货报错: {}"),
  NOT_FOUND("800404", "信息未找到: {}"),

  ;

  private final String code;
  private final String message;
}
