package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 网上渠道经办人账号 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record InternetUserId(Long value) implements Identifier<Long> {

  public InternetUserId {
    Objects.requireNonNull(value, "InternetUserId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("InternetUserId value must be positive");
    }
  }

  public static InternetUserId of(Long value) {
    return new InternetUserId(value);
  }

  @Override
  public String toString() {
    return "InternetUserId{" + value + "}";
  }
}
