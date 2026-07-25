package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 凭据 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record CredentialId(Long value) implements Identifier<Long> {

  public CredentialId {
    Objects.requireNonNull(value, "CredentialId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("CredentialId value must be positive");
    }
  }

  public static CredentialId of(Long value) {
    return new CredentialId(value);
  }

  @Override
  public String toString() {
    return "CredentialId{" + value + "}";
  }
}
