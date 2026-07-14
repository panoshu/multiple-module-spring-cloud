package com.example.approval.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 匹配审批流请求
 *
 * @author approval-service
 */
public record MatchApprovalFlowRequest(
    /**
     * 业务类型
     */
    @NotBlank(message = "业务类型不能为空")
    String businessType,
    /**
     * 账管人编码
     */
    @NotBlank(message = "账管人编码不能为空")
    String accountManagerCode,
    /**
     * 金额（可选）
     */
    Long amount
) {
}