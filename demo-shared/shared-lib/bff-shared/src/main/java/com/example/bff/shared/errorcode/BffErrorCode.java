package com.example.bff.shared.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * BFF 层错误码
 *
 * @author bff
 */
public enum BffErrorCode implements ErrorDefinition {

  ROUTE_NOT_FOUND("SERVICE.BFF.0001", "未找到业务类型路由"),
  INVALID_CHANNEL_SCOPE("SERVICE.BFF.0002", "无效的渠道范围配置");

  private final String code;
  private final String message;

  BffErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String getCode() {
    return this.code;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
