package com.example.approval.api.response;

import com.example.approval.types.ApprovalInstanceId;

/**
 * 审批实例ID响应
 *
 * @author approval-service
 */
public record ApprovalInstanceIdResponse(
  /**
   * 审批实例ID
   */
  ApprovalInstanceId instanceId
) {
}
