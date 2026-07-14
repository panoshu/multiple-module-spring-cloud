package com.example.approval.types.enums;

/**
 * 审批实例状态
 *
 * @author approval-service
 */
public enum InstanceStatus {
  /**
   * 待审批
   */
  PENDING,

  /**
   * 审批中
   */
  APPROVING,

  /**
   * 已通过
   */
  APPROVED,

  /**
   * 已拒绝
   */
  REJECTED,

  /**
   * 已撤回
   */
  WITHDRAWN
}