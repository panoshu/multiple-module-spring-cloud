package com.example.approval.api.dto;

import com.example.approval.types.NodeId;

import java.time.LocalDateTime;

/**
 * 节点执行DTO
 *
 * @author approval-service
 */
public record NodeExecutionDTO(
    /**
     * 节点ID
     */
    NodeId nodeId,
    /**
     * 节点名称
     */
    String nodeName,
    /**
     * 执行状态：PENDING-待执行，APPROVED-已通过，REJECTED-已驳回，TRANSFERRED-已转交
     */
    String status,
    /**
     * 审批人
     */
    String approver,
    /**
     * 审批意见
     */
    String comment,
    /**
     * 审批时间
     */
    LocalDateTime approvedAt
) {
}