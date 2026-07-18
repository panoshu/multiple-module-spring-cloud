package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.ValidationScope;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record ValidationRule(
    ValidationScope scope, String expr, String message
) implements ValueObject {
  public ValidationRule {
    if (scope == null) scope = ValidationScope.ROW;
    if (expr == null || expr.isBlank()) throw new IllegalArgumentException("ValidationRule.expr empty");
  }
}
