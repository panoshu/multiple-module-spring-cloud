package com.example.approval.types;

import com.example.shared.identifier.contract.Identifier;

/**
 * 审批实例ID
 *
 * @author approval-service
 */
public record ApprovalInstanceId(Long value) implements Identifier<Long> {

  public ApprovalInstanceId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("ApprovalInstanceId must be positive");
    }
  }

  public static ApprovalInstanceId of(Long value) {
    return new ApprovalInstanceId(value);
  }
}
