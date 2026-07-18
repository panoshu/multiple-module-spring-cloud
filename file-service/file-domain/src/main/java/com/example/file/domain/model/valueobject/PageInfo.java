package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record PageInfo(
    String tableCode, int totalCount, int startPos, int returnedCount, boolean hasMore
) implements ValueObject {
  public static PageInfo of(String tableCode, int totalCount, FetchPagination pagination, int returnedCount) {
    return new PageInfo(
        tableCode, totalCount, pagination.startPos(), returnedCount,
        pagination.startPos() + returnedCount < totalCount
    );
  }
}
