package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record FetchPagination(
  String tableCode, int startPos, int pageSize
) implements ValueObject {
  public FetchPagination {
    if (tableCode == null || tableCode.isBlank()) throw new IllegalArgumentException("FetchPagination.tableCode empty");
    if (pageSize > 2000) pageSize = 2000;
    if (pageSize < 1) pageSize = 1000;
    startPos = Math.max(0, startPos);
  }

  public static FetchPagination of(String tableCode, int startPos, int pageSize) {
    return new FetchPagination(tableCode, startPos, pageSize);
  }

  public int endPos() {
    return startPos + pageSize;
  }
}
