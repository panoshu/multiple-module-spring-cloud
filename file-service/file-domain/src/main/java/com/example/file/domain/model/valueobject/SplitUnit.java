package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Map;

public record SplitUnit(
  String splitKey,
  Map<String, Object> data
) implements ValueObject {
  public SplitUnit {
    data = data == null ? Map.of() : Map.copyOf(data);
  }
}
