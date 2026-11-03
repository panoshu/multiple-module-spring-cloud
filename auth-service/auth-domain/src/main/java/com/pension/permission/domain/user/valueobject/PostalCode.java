package com.pension.permission.domain.user.valueobject;

import java.util.Objects;

/**
 * PostalCode
 *
 * @author panoshu
 * @since 2026/8/1
 */
public record PostalCode(String value) {

  public PostalCode {
    Objects.requireNonNull(value);
    value = value.trim();

    if (!value.matches("\\d{6}")) {
      throw new IllegalArgumentException("Invalid postal code");
    }
  }
}
