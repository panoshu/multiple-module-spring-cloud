package com.example.annuity.types;

import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

/**
 * 受理编号
 *
 * @author panoshu
 * @since 2026/5/12
 */
public record AcceptanceNo(String value) implements Identifier<String> {

  public AcceptanceNo {
    Objects.requireNonNull(value, "AcceptanceNo value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AcceptanceNo value cannot be blank");
    }
  }

  public static AcceptanceNo of(String value) {
    return new AcceptanceNo(value);
  }

  @Override
  public String toString() {
    return "AcceptanceNo{" + value + "}";
  }
}
