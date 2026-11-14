package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批转交请求
 *
 * @author approval-service
 */
public record TransferRequest(
  /**
   * 审批实例ID
   */
  @NotNull(message = "审批实例ID不能为空")
  ApprovalInstanceId instanceId,
  /**
   * 兼容字段：服务端已忽略该值（批次 57，越权治理）。
   * 当前审批人取自带签名的会话账号，调用方无法通过本字段冒充他人转交。
   */
  @NotBlank(message = "当前审批人不能为空")
  String currentApprover,
  /**
   * 目标审批人（业务参数，服务端保留）
   */
  @NotBlank(message = "目标审批人不能为空")
  String targetApprover,
  /**
   * 转交原因
   */
  String reason
) {
}
