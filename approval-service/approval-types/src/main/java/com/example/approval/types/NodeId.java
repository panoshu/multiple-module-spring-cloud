package com.example.approval.types;

import com.example.shared.identifier.contract.Identifier;

/**
 * 审批节点ID
 *
 * @author approval-service
 */
public record NodeId(Long value) implements Identifier<Long> {

  public NodeId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("NodeId must be positive");
    }
  }

  public static NodeId of(Long value) {
    return new NodeId(value);
  }
}
