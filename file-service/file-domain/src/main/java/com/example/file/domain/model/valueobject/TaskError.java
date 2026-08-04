package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record TaskError(
  String code, String message, String detail
) implements ValueObject {
  public TaskError {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("TaskError.code empty");
  }
}
