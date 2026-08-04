package com.example.file.domain.model.valueobject.parse;

import java.util.Map;

public record KvRegionResult(
  String regionName,
  Map<String, Object> data
) implements RegionParseResult {
  public KvRegionResult {
    data = data == null ? Map.of() : Map.copyOf(data);
  }
}
