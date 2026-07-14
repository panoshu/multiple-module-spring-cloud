package com.example.approval.types.enums;

/**
 * 执行状态
 *
 * @author approval-service
 */
public enum ExecutionStatus {
  /**
   * 待执行
   */
  PENDING,

  /**
   * 已通过
   */
  APPROVED,

  /**
   * 已拒绝
   */
  REJECTED,

  /**
   * 已跳过
   */
  SKIPPED
}