package com.example.file.domain.model.valueobject.parse;

import java.util.List;
import java.util.Map;

public record TableRegionResult(
    String regionName,
    List<Map<String, Object>> rows
) implements RegionParseResult {
  public TableRegionResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
