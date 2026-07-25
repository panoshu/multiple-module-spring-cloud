package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 网点渠道柜员账号 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record BranchUserId(Long value) implements Identifier<Long> {

  public BranchUserId {
    Objects.requireNonNull(value, "BranchUserId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("BranchUserId value must be positive");
    }
  }

  public static BranchUserId of(Long value) {
    return new BranchUserId(value);
  }

  @Override
  public String toString() {
    return "BranchUserId{" + value + "}";
  }
}
