package com.example.file.domain.model.parse.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.FieldType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record PropertyFieldDef(
  String code, FieldType type, boolean required, String pattern
) implements ValueObject {
  public PropertyFieldDef {
    if (code == null || code.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("PropertyFieldDef.code empty");
    }
    if (type == null) {
      type = FieldType.STRING;
    }
  }
}
