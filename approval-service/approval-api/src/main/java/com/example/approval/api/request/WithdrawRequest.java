package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发起人撤回请求
 *
 * @author approval-service
 */
public record WithdrawRequest(
  /**
   * 审批实例ID
   */
  @NotNull(message = "审批实例ID不能为空")
  ApprovalInstanceId instanceId,
  /**
   * 兼容字段：服务端已忽略该值（批次 57，越权治理）。
   * 发起人取自带签名的会话账号，领域层再校验其等于审批实例创建人。
   */
  @NotBlank(message = "发起人不能为空")
  String initiator,
  /**
   * 撤回原因
   */
  String reason
) {
}
