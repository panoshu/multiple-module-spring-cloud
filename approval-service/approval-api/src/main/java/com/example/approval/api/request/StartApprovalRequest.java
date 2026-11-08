package com.example.approval.api.request;

import com.example.approval.types.ApprovalFlowId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启动审批请求
 *
 * @author approval-service
 */
public record StartApprovalRequest(
  /**
   * 审批流ID
   */
  @NotNull(message = "审批流ID不能为空")
  ApprovalFlowId flowId,
  /**
   * 业务单号
   */
  @NotBlank(message = "业务单号不能为空")
  String businessNo,
  /**
   * 业务类型
   */
  @NotBlank(message = "业务类型不能为空")
  String businessType,
  /**
   * 发起人
   */
  @NotBlank(message = "发起人不能为空")
  String initiator
) {
}
