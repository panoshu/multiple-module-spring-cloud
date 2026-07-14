package com.example.approval.api.dto;

import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.RecordId;

import java.time.LocalDateTime;

/**
 * 审批记录DTO
 *
 * @author approval-service
 */
public record ApprovalRecordDTO(
    /**
     * 记录ID
     */
    RecordId recordId,
    /**
     * 审批实例ID
     */
    ApprovalInstanceId instanceId,
    /**
     * 节点名称
     */
    String nodeName,
    /**
     * 操作类型：APPROVE-通过，REJECT-驳回，TRANSFER-转交，WITHDRAW-撤回
     */
    String actionType,
    /**
     * 操作人
     */
    String operator,
    /**
     * 审批意见
     */
    String comment,
    /**
     * 操作时间
     */
    LocalDateTime operatedAt
) {
}