package com.example.bff.shared.business;

/**
 * BFF 聚合调用业务异常信号。
 *
 * <p>当主实体（批次详情）返回业务错误时，通过此异常将错误码和消息传播到聚合调用方，
 * 而非静默降级为 null。
 *
 * @author bff
 */
class BffAggregationException extends RuntimeException {

  final String code;
  final String message;

  BffAggregationException(String code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }
}
