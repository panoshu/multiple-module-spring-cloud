package com.example.file.domain.model.valueobject.parse;

import java.util.Map;

public record RawRow(
  int rowIndex,
  Map<Integer, String> cells,
  boolean isBlank
) {
  public RawRow {
    cells = cells == null ? Map.of() : Map.copyOf(cells);
  }

  public static RawRow of(int rowIndex, Map<Integer, String> cells, boolean isBlank) {
    return new RawRow(rowIndex, cells, isBlank);
  }
}
