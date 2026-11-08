package com.example.file.domain.model.parse.valueobject;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record TaskError(
  String code, String message, String detail
) implements ValueObject {
  public TaskError {
    if (code == null || code.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("TaskError.code empty");
    }
  }
}
