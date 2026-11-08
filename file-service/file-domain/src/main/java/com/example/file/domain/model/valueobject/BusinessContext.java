package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Map;

public record BusinessContext(Map<String, Object> variables) implements ValueObject {
  public BusinessContext {
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  public static BusinessContext empty() {
    return new BusinessContext(Map.of());
  }

  public BusinessContext with(String key, Object value) {
    var m = new java.util.LinkedHashMap<>(variables);
    m.put(key, value);
    return new BusinessContext(Map.copyOf(m));
  }
}
