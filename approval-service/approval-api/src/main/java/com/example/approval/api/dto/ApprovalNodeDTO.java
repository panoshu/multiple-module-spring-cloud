package com.example.approval.api.dto;

import com.example.approval.types.NodeId;

import java.util.List;

/**
 * 审批节点DTO
 *
 * @author approval-service
 */
public record ApprovalNodeDTO(
    /**
     * 节点ID
     */
    NodeId nodeId,
    /**
     * 节点名称
     */
    String nodeName,
    /**
     * 节点类型：ROLE-角色审批，USER-指定用户审批
     */
    String nodeType,
    /**
     * 审批角色（nodeType=ROLE时）
     */
    String approvalRole,
    /**
     * 审批人列表（nodeType=USER时）
     */
    List<String> approvalUsers,
    /**
     * 节点顺序
     */
    int order,
    /**
     * 是否必须审批
     */
    boolean required
) {
}