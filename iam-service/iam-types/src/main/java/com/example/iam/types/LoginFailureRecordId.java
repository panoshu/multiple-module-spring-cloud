package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 登录失败记录 ID(LoginLog 聚合内的子实体标识)。
 *
 * <p>每次登录失败生成一条 {@code LoginFailureRecord},记录失败原因与时间。
 * 多条记录可关联同一 LoginLog(理论上一次登录可能因多重原因失败)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record LoginFailureRecordId(Long value) implements Identifier<Long> {

  public LoginFailureRecordId {
    Objects.requireNonNull(value, "LoginFailureRecordId value cannot be null");
  }

  public static LoginFailureRecordId of(Long value) {
    return new LoginFailureRecordId(value);
  }

  public static LoginFailureRecordId of(String value) {
    return new LoginFailureRecordId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
