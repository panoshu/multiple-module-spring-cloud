package com.pension.permission.domain.product;

import com.example.shared.exception.ErrorDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductError
  implements ErrorDefinition {

  PLAN_NOT_FOUND("PRODUCT-001", "计划不存在"),
  CUSTOMER_NOT_FOUND("PRODUCT-002", "客户不存在"),
  PRODUCT_NOT_FOUND("PRODUCT-003", "产品不存在"),

  ;

  private final String code;
  private final String message;

}
