package com.example.file.domain.model.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.ValidationScope;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record ValidationRule(
  String field,
  ValidationScope scope,
  String expr,
  String message,
  FieldType type
) implements ValueObject {
  public ValidationRule {
    scope = scope == null ? ValidationScope.ROW : scope;
    if (expr == null || expr.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("ValidationRule.expr empty");
    }
    type = type == null ? FieldType.STRING : type;
  }
}
