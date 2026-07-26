package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 统一用户 ID(三渠道共用:网上/总部/网点)。
 *
 * <p>三渠道用户共用同一 ID 空间,不再区分 InternetUserId/HqUserId/BranchUserId。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserId(Long value) implements Identifier<Long> {

  public UserId {
    Objects.requireNonNull(value, "UserId value cannot be null");
  }

  public static UserId of(Long value) {
    return new UserId(value);
  }

  public static UserId of(String value) {
    return new UserId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
