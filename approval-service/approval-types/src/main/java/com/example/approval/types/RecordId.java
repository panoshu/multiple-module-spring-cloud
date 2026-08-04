package com.example.approval.types;

import com.example.shared.identifier.contract.Identifier;

/**
 * 审批记录ID
 *
 * @author approval-service
 */
public record RecordId(Long value) implements Identifier<Long> {

  public RecordId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("RecordId must be positive");
    }
  }

  public static RecordId of(Long value) {
    return new RecordId(value);
  }
}
