package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;
import java.util.Objects;

/**
 * 计划代办关系 ID(企业年金计划代办授权关系的标识)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PlanDelegationId(Long value) implements Identifier<Long> {

  public PlanDelegationId {
    Objects.requireNonNull(value, "PlanDelegationId value cannot be null");
  }

  public static PlanDelegationId of(Long value) {
    return new PlanDelegationId(value);
  }

  public static PlanDelegationId of(String value) {
    return new PlanDelegationId(Long.parseLong(value));
  }

  public long longValue() {
    return value;
  }
}
