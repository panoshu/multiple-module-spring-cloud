package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotNull;

/**
 * 审批历史请求
 *
 * @author approval-service
 */
public record GetApprovalHistoryRequest(
  /**
   * 审批实例ID
   */
  @NotNull(message = "审批实例ID不能为空")
  ApprovalInstanceId instanceId
) {
}
