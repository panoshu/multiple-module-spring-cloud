package com.example.core.types.identity;

import java.util.Objects;

/**
 * 身份证件值对象
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record IdentityDocument(
  IdentityType type,
  DocumentNumber number
) {
  public IdentityDocument {
    Objects.requireNonNull(type, "Identity type required");
    Objects.requireNonNull(number, "Identity number required");
    validate(type, number);
  }

  private static void validate(IdentityType type, DocumentNumber number) {
    if (type == IdentityType.ID_CARD) {
      validateIdCard(number);
    }
  }

  private static void validateIdCard(DocumentNumber number) {
    int length = number.length();
    if (length != 15 && length != 18) {
      throw new IllegalArgumentException("ID card number must be 15 or 18 digits");
    }
    if (length == 15) {
      if (!number.value().matches("\\d{15}")) {
        throw new IllegalArgumentException("15 digit ID card number must contain digits only");
      }
    }
    if (!number.value().matches("\\d{17}[0-9Xx]")) {
      throw new IllegalArgumentException("18 digit ID card number format invalid");
    }
  }
}
