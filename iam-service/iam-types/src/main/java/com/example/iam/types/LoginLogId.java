package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 登录日志 ID(用户登录成功/失败日志的标识)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record LoginLogId(Long value) implements Identifier<Long> {

  public LoginLogId {
    Objects.requireNonNull(value, "LoginLogId value cannot be null");
  }

  public static LoginLogId of(Long value) {
    return new LoginLogId(value);
  }

  public static LoginLogId of(String value) {
    return new LoginLogId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
