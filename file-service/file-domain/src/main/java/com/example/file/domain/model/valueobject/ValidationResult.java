package com.example.file.domain.model.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record ValidationResult(List<RowError> errors) implements ValueObject {
  public ValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
  public boolean isValid() { return errors.isEmpty(); }
  public static ValidationResult empty() { return new ValidationResult(List.of()); }
  public static ValidationResult of(List<RowError> errors) { return new ValidationResult(errors); }
}
