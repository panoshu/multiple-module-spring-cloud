package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 二次授权会话 ID(网点渠道二次授权流程的会话标识)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record SecondaryAuthSessionId(Long value) implements Identifier<Long> {

  public SecondaryAuthSessionId {
    Objects.requireNonNull(value, "SecondaryAuthSessionId value cannot be null");
  }

  public static SecondaryAuthSessionId of(Long value) {
    return new SecondaryAuthSessionId(value);
  }

  public static SecondaryAuthSessionId of(String value) {
    return new SecondaryAuthSessionId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
