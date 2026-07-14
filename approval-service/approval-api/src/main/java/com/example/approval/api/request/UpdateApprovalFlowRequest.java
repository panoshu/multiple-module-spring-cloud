package com.example.approval.api.request;

import com.example.approval.api.dto.ApprovalNodeDTO;
import com.example.approval.api.dto.MatchRulesDTO;
import com.example.approval.types.ApprovalFlowId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新审批流请求
 *
 * @author approval-service
 */
public record UpdateApprovalFlowRequest(
    /**
     * 审批流ID
     */
    @NotNull(message = "审批流ID不能为空")
    ApprovalFlowId flowId,
    /**
     * 审批流名称
     */
    @NotBlank(message = "审批流名称不能为空")
    String flowName,
    /**
     * 匹配规则
     */
    @NotNull(message = "匹配规则不能为空")
    @Valid
    MatchRulesDTO matchRules,
    /**
     * 审批节点列表
     */
    @NotEmpty(message = "审批节点列表不能为空")
    @Valid
    List<ApprovalNodeDTO> nodes,
    /**
     * 更新人
     */
    @NotBlank(message = "更新人不能为空")
    String updatedBy
) {
}