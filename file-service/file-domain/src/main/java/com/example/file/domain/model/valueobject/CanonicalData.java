package com.example.file.domain.model.valueobject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CanonicalData {
  private final Map<String, Object> properties = new LinkedHashMap<>();
  private final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();

  public static CanonicalData empty() {
    return new CanonicalData();
  }

  public static CanonicalData of(Map<String, Object> properties,
                                 Map<String, List<Map<String, Object>>> tables) {
    CanonicalData data = new CanonicalData();
    if (properties != null) data.properties.putAll(properties);
    if (tables != null) tables.forEach((k, v) -> data.tables.put(k, new ArrayList<>(v)));
    return data;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public Map<String, List<Map<String, Object>>> tables() {
    return tables;
  }

  public void setProperty(String key, Object value) {
    properties.put(key, value);
  }
}
