package com.pension.permission.domain.product;

import com.example.shared.exception.ErrorDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductError
  implements ErrorDefinition {

  PLAN_NOT_FOUND("SERVICE.AUTH.0701", "计划不存在"),
  CUSTOMER_NOT_FOUND("SERVICE.AUTH.0702", "客户不存在"),
  PRODUCT_NOT_FOUND("SERVICE.AUTH.0703", "产品不存在"),

  ;

  private final String code;
  private final String message;

}
