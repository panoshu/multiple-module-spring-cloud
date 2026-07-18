package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record FieldDef(
    String code, FieldType type, boolean required, Integer scale
) implements ValueObject {
  public FieldDef {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("FieldDef.code empty");
    if (type == null) type = FieldType.STRING;
  }
}
