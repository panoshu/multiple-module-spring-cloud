package com.example.bff.shared.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * BFF 层错误码
 *
 * @author bff
 */
public enum BffErrorCode implements ErrorDefinition {

  SCENARIO_NOT_SUPPORTED("SERVICE.BFF.0001", "不支持的业务场景");

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
