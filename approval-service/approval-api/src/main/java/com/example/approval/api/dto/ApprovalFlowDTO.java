package com.example.approval.api.dto;

import com.example.approval.types.ApprovalFlowId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批流DTO
 *
 * @author approval-service
 */
public record ApprovalFlowDTO(
    /**
     * 审批流ID
     */
    ApprovalFlowId flowId,
    /**
     * 审批流名称
     */
    String flowName,
    /**
     * 业务类型
     */
    String businessType,
    /**
     * 状态：ACTIVE-有效，DEPRECATED-已废弃
     */
    String status,
    /**
     * 版本号
     */
    int version,
    /**
     * 匹配规则
     */
    MatchRulesDTO matchRules,
    /**
     * 审批节点列表
     */
    List<ApprovalNodeDTO> nodes,
    /**
     * 创建人
     */
    String createdBy,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
}