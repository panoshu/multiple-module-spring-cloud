package com.example.approval.api.response;

import com.example.approval.types.ApprovalFlowId;

/**
 * 审批流ID响应
 *
 * @author approval-service
 */
public record ApprovalFlowIdResponse(
  /**
   * 审批流ID
   */
  ApprovalFlowId flowId
) {
}
