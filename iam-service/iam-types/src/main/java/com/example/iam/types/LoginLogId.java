package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 登录日志 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record LoginLogId(Long value) implements Identifier<Long> {

  public LoginLogId {
    Objects.requireNonNull(value, "LoginLogId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("LoginLogId value must be positive");
    }
  }

  public static LoginLogId of(Long value) {
    return new LoginLogId(value);
  }

  @Override
  public String toString() {
    return "LoginLogId{" + value + "}";
  }
}
