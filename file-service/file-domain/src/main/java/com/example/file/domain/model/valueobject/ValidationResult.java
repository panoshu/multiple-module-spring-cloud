package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record ValidationResult(List<ValidationError> errors) implements ValueObject {
  public ValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static ValidationResult empty() {
    return new ValidationResult(List.of());
  }

  public static ValidationResult of(List<ValidationError> errors) {
    return new ValidationResult(errors);
  }

  public boolean passed() {
    return errors.isEmpty();
  }

  public boolean isValid() {
    return errors.isEmpty();
  }
}
