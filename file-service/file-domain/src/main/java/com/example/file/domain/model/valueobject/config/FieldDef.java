package com.example.file.domain.model.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record FieldDef(
  String code, FieldType type, boolean required, Integer scale
) implements ValueObject {
  public FieldDef {
    if (code == null || code.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("FieldDef.code empty");
    }
    if (type == null) {
      type = FieldType.STRING;
    }
  }
}
