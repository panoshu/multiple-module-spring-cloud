package com.example.approval.api.request;

import com.example.approval.types.ApprovalFlowId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 废弃审批流请求
 *
 * @author approval-service
 */
public record DeprecateApprovalFlowRequest(
  /**
   * 审批流ID
   */
  @NotNull(message = "审批流ID不能为空")
  ApprovalFlowId flowId,
  /**
   * 操作人
   */
  @NotBlank(message = "操作人不能为空")
  String operatedBy
) {
}
