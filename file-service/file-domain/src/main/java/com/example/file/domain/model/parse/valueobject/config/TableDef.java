package com.example.file.domain.model.parse.valueobject.config;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record TableDef(String code, List<FieldDef> fields) implements ValueObject {
  public TableDef {
    if (code == null || code.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("TableDef.code empty");
    }
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
