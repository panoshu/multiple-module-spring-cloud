package com.example.annuity.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 年金员工明细 ID
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeDetailId(String value) implements Identifier<String> {

  public AnnuityEmployeeDetailId {
    Objects.requireNonNull(value, "AnnuityEmployeeDetailId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AnnuityEmployeeDetailId value cannot be blank");
    }
  }

  public static AnnuityEmployeeDetailId of(String value) {
    return new AnnuityEmployeeDetailId(value);
  }

  @Override
  public String toString() {
    return "AnnuityEmployeeDetailId{" + value + "}";
  }
}
