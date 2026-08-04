package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record RowError(
  int rowIndex, String tableCode, String expr, String message
) implements ValueObject {
}
