package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批通过请求
 *
 * @author approval-service
 */
public record ApproveRequest(
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
   * 审批意见
   */
  String comment
) {
}
