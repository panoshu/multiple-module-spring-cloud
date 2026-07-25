package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 二次授权会话 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record SecondaryAuthSessionId(Long value) implements Identifier<Long> {

  public SecondaryAuthSessionId {
    Objects.requireNonNull(value, "SecondaryAuthSessionId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("SecondaryAuthSessionId value must be positive");
    }
  }

  public static SecondaryAuthSessionId of(Long value) {
    return new SecondaryAuthSessionId(value);
  }

  @Override
  public String toString() {
    return "SecondaryAuthSessionId{" + value + "}";
  }
}
