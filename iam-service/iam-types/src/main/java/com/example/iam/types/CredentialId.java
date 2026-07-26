package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 凭据 ID(密码/UKey/动态令牌等凭据的统一标识)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record CredentialId(Long value) implements Identifier<Long> {

  public CredentialId {
    Objects.requireNonNull(value, "CredentialId value cannot be null");
  }

  public static CredentialId of(Long value) {
    return new CredentialId(value);
  }

  public static CredentialId of(String value) {
    return new CredentialId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
