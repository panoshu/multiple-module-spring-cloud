package com.example.file.domain.model.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Map;

public record TargetMapping(
  String targetTemplateRef,
  Map<String, String> fieldMappings
) implements ValueObject {
  public TargetMapping {
    fieldMappings = fieldMappings == null ? Map.of() : Map.copyOf(fieldMappings);
  }
}
