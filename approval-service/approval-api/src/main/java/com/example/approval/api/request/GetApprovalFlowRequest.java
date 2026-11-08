package com.example.approval.api.request;

import com.example.approval.types.ApprovalFlowId;
import jakarta.validation.constraints.NotNull;

/**
 * 查询审批流请求
 *
 * @author approval-service
 */
public record GetApprovalFlowRequest(
  /**
   * 审批流ID
   */
  @NotNull(message = "审批流ID不能为空")
  ApprovalFlowId flowId
) {
}
