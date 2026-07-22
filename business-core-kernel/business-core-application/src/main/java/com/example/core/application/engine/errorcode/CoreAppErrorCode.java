package com.example.core.application.engine.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:28
 */
@AllArgsConstructor
public enum CoreAppErrorCode implements ErrorDefinition {

  INVALID_STATUS("100001", "[状态有误]{}"),
  INVALID_DATA("100002", "[数据有误]{}"),
  INVALID_OPERATION("100003", "[操作有误]{}"),

  DATA_NOT_FOUND("101001", "[数据未找到]{}"),

  STEP_HANDLER_FAILED("102001", "[步骤处理器执行失败]{}"),

  INVALIDATE("103001", "[校验失败]{}"),

  ;

  final String code;
  final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
