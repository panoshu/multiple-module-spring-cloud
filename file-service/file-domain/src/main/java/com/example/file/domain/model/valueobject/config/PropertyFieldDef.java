package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record PropertyFieldDef(
  String code, FieldType type, boolean required, String pattern
) implements ValueObject {
  public PropertyFieldDef {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("PropertyFieldDef.code empty");
    if (type == null) type = FieldType.STRING;
  }
}
