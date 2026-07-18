package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record FieldLocation(
    String tableCode,
    String fieldName
) implements ValueObject {
  public static FieldLocation parse(String path) {
    int dot = path.indexOf('.');
    if (dot < 0) return new FieldLocation(null, path);
    return new FieldLocation(path.substring(0, dot), path.substring(dot + 1));
  }
  public boolean isProperty() { return tableCode == null; }
}
