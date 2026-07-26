package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 权限规则 ID(权限聚合根的标识)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionRuleId(Long value) implements Identifier<Long> {

  public PermissionRuleId {
    Objects.requireNonNull(value, "PermissionRuleId value cannot be null");
  }

  public static PermissionRuleId of(Long value) {
    return new PermissionRuleId(value);
  }

  public static PermissionRuleId of(String value) {
    return new PermissionRuleId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
