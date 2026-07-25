package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 总部渠道运营人员账号 ID
 *
 * @author iam-service
 * @since 2026/7/25
 */
public record HqUserId(Long value) implements Identifier<Long> {

  public HqUserId {
    Objects.requireNonNull(value, "HqUserId value cannot be null");
    if (value <= 0) {
      throw new IllegalArgumentException("HqUserId value must be positive");
    }
  }

  public static HqUserId of(Long value) {
    return new HqUserId(value);
  }

  @Override
  public String toString() {
    return "HqUserId{" + value + "}";
  }
}
