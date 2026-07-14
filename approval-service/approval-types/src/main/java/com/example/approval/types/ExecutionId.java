package com.example.approval.types;

import com.example.shared.primitives.identity.Identifier;

/**
 * 节点执行ID
 *
 * @author approval-service
 */
public record ExecutionId(Long value) implements Identifier<Long> {

  public ExecutionId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("ExecutionId must be positive");
    }
  }

  public static ExecutionId of(Long value) {
    return new ExecutionId(value);
  }
}