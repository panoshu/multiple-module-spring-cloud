package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record DerivationRule(
  String field,
  String expr,
  FieldType type,
  String description
) implements ValueObject {
  public DerivationRule {
    if (field == null || field.isBlank()) throw new IllegalArgumentException("DerivationRule.field empty");
    if (expr == null || expr.isBlank()) throw new IllegalArgumentException("DerivationRule.expr empty");
    type = type == null ? FieldType.STRING : type;
  }
}
