package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批驳回请求
 *
 * @author approval-service
 */
public record RejectRequest(
  /**
   * 审批实例ID
   */
  @NotNull(message = "审批实例ID不能为空")
  ApprovalInstanceId instanceId,
  /**
   * 审批人
   */
  @NotBlank(message = "审批人不能为空")
  String approver,
  /**
   * 驳回原因
   */
  @NotBlank(message = "驳回原因不能为空")
  String reason
) {
}
