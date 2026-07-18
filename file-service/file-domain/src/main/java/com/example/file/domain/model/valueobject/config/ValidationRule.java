package com.example.file.domain.model.valueobject.config;

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
    if (expr == null || expr.isBlank()) throw new IllegalArgumentException("ValidationRule.expr empty");
    type = type == null ? FieldType.STRING : type;
  }
}
