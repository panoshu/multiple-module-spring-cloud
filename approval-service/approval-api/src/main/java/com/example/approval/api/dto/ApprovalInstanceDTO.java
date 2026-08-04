package com.example.approval.api.dto;

import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批实例DTO
 *
 * @author approval-service
 */
public record ApprovalInstanceDTO(
  /**
   * 审批实例ID
   */
  ApprovalInstanceId instanceId,
  /**
   * 审批流ID
   */
  ApprovalFlowId flowId,
  /**
   * 业务单号
   */
  String businessNo,
  /**
   * 业务类型
   */
  String businessType,
  /**
   * 实例状态：PENDING-待审批，APPROVED-已通过，REJECTED-已驳回，WITHDRAWN-已撤回
   */
  String status,
  /**
   * 发起人
   */
  String initiator,
  /**
   * 当前审批节点ID
   */
  String currentNodeId,
  /**
   * 节点执行列表
   */
  List<NodeExecutionDTO> nodeExecutions,
  /**
   * 创建时间
   */
  LocalDateTime createdAt,
  /**
   * 完成时间
   */
  LocalDateTime completedAt
) {
}
