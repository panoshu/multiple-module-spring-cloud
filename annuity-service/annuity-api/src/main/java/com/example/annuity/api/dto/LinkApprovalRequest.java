package com.example.annuity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 跨服务调用审批服务请求
 *
 * @param approver 审批人标识
 * @author annuity-service
 */
public record LinkApprovalRequest(
    @NotBlank(message = "审批人不能为空")
    String approver
) {
}
