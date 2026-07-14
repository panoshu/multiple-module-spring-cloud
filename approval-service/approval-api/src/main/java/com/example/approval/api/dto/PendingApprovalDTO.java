package com.example.approval.api.dto;

import com.example.approval.types.ApprovalInstanceId;

import java.time.LocalDateTime;

/**
 * 待审批DTO
 *
 * @author approval-service
 */
public record PendingApprovalDTO(
    /**
     * 审批实例ID
     */
    ApprovalInstanceId instanceId,
    /**
     * 业务单号
     */
    String businessNo,
    /**
     * 业务类型
     */
    String businessType,
    /**
     * 审批流名称
     */
    String flowName,
    /**
     * 当前节点名称
     */
    String currentNodeName,
    /**
     * 发起人
     */
    String initiator,
    /**
     * 发起时间
     */
    LocalDateTime createdAt
) {
}