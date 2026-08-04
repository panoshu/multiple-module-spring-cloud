package com.example.file.domain.model.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record CanonicalModelDef(
  List<PropertyFieldDef> properties,
  List<TableDef> tables
) implements ValueObject {
  public CanonicalModelDef {
    properties = properties == null ? List.of() : List.copyOf(properties);
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
