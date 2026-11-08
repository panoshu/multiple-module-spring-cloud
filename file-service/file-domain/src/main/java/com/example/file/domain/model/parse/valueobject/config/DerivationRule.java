package com.example.file.domain.model.parse.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record DerivationRule(
  String field,
  String expr,
  FieldType type,
  String description
) implements ValueObject {
  public DerivationRule {
    if (field == null || field.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("DerivationRule.field empty");
    }
    if (expr == null || expr.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("DerivationRule.expr empty");
    }
    type = type == null ? FieldType.STRING : type;
  }
}
