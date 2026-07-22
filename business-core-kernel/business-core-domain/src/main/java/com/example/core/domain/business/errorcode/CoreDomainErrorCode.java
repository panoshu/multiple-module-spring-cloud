package com.example.core.domain.business.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 10:28
 */
public enum CoreDomainErrorCode implements ErrorDefinition {

  INVALID_STATUS("200001", "[状态有误]{}"),
  INVALID_DATA("200002", "[数据有误]{}"),
  INVALID_OPERATION("200003", "[操作有误]{}"),

  ;

  final String code;
  final String message;

  CoreDomainErrorCode(String code, String message) {
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
