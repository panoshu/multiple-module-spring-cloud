package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 业务定义 ID(可办理的业务类型标识,如:年金申请、账户转移)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record BusinessDefinitionId(Long value) implements Identifier<Long> {

  public BusinessDefinitionId {
    Objects.requireNonNull(value, "BusinessDefinitionId value cannot be null");
  }

  public static BusinessDefinitionId of(Long value) {
    return new BusinessDefinitionId(value);
  }

  public static BusinessDefinitionId of(String value) {
    return new BusinessDefinitionId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
