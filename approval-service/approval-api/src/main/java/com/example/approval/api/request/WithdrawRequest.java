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
     * 发起人
     */
    @NotBlank(message = "发起人不能为空")
    String initiator,
    /**
     * 撤回原因
     */
    String reason
) {
}