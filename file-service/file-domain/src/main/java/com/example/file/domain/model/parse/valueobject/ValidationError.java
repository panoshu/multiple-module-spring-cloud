package com.example.file.domain.model.parse.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record ValidationError(
  String field,
  String message,
  String expression
) implements ValueObject {
}
