package com.example.approval.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 节点执行ID
 *
 * @author approval-service
 */
@IdDefinition(type = IdType.SNOWFLAKE)
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
