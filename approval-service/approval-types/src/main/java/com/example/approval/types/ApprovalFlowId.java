package com.example.approval.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 审批流ID
 *
 * @author approval-service
 */
@IdDefinition(type = IdType.SNOWFLAKE)
public record ApprovalFlowId(Long value) implements Identifier<Long> {

  public ApprovalFlowId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("ApprovalFlowId must be positive");
    }
  }

  public static ApprovalFlowId of(Long value) {
    return new ApprovalFlowId(value);
  }
}
