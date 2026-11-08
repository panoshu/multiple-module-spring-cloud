package com.example.approval.api.request;

import com.example.approval.types.ApprovalInstanceId;
import jakarta.validation.constraints.NotNull;

/**
 * 查询审批实例请求
 *
 * @author approval-service
 */
public record GetApprovalInstanceRequest(
  /**
   * 审批实例ID
   */
  @NotNull(message = "审批实例ID不能为空")
  ApprovalInstanceId instanceId
) {
}
