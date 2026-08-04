package com.example.annuity.types;

import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

/**
 * 年金员工明细批次 ID
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeBatchId(String value) implements Identifier<String> {

  public AnnuityEmployeeBatchId {
    Objects.requireNonNull(value, "AnnuityEmployeeBatchId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AnnuityEmployeeBatchId value cannot be blank");
    }
  }

  public static AnnuityEmployeeBatchId of(String value) {
    return new AnnuityEmployeeBatchId(value);
  }

  @Override
  public String toString() {
    return "AnnuityEmployeeBatchId{" + value + "}";
  }
}
