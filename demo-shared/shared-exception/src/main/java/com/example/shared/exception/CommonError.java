package com.example.shared.exception;


public enum CommonError implements ErrorDefinition {

  // ==================== 通用成功（通常用于统一响应封装） ====================
  SUCCESS("00000", "操作成功"),

  // ==================== 客户端请求错误 (4xx 系列) ====================
  BAD_REQUEST("A0400", "请求参数错误"),
  UNAUTHORIZED("A0401", "未登录或登录已过期"),
  FORBIDDEN("A0403", "无权限访问"),
  NOT_FOUND("A0404", "请求资源不存在"),
  METHOD_NOT_ALLOWED("A0405", "请求方法不允许"),
  TOO_MANY_REQUESTS("A0429", "请求过于频繁，请稍后再试"),

  // ==================== 系统执行错误 (5xx 系列) ====================
  INTERNAL_SERVER_ERROR("B0001", "系统开小差了，请稍后再试"),
  SERVICE_DEGRADATION("B0002", "系统负载过高，服务降级"),
  REMOTE_SERVICE_ERROR("B0003", "第三方服务调用失败"),
  NETWORK_ERROR("B0004", "网络异常，请稍后再试"),
  CONCURRENCY_ERROR("B0005", "并发错误"),
  TIMEOUT_ERROR("B0006", "请求超时"),
  UNKNOW_ERROR("B9999", "系统开小差了，请联系管理员");

  private final String code;
  private final String message;

  CommonError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return this.message;
  }
}
