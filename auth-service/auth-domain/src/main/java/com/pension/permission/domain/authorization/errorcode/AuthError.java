package com.pension.permission.domain.authorization.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthError
  implements ErrorDefinition {

  PLAN_NOT_FOUND("AUTH-001", "计划不存在"),

  ;

  private final String code;
  private final String message;

}
