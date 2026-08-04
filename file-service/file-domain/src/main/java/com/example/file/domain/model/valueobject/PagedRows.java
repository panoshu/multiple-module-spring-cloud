package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Map;

public record PagedRows(
  List<Map<String, Object>> rows, PageInfo pageInfo
) implements ValueObject {
  public PagedRows {
    rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
