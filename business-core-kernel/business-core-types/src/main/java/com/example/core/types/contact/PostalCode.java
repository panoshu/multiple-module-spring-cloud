package com.example.core.types.contact;

import java.util.Objects;

/**
 * 邮政编码值对象
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
