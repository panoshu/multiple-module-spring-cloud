package com.example.shared.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/6 19:03
 */
public enum SharedDomainErrorCode implements ErrorDefinition {

  ENTITY_NOT_FOUND("100404", "[实体不存在]{}"),
  INVALID_DATA("100002", "[数据有误]{}"),
  INVALID_OPERATION("100003", "[操作有误]{}"),

  ;

  final String code;
  final String message;

  SharedDomainErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return "";
  }
}
