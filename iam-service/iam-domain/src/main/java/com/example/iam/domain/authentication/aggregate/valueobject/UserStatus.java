package com.example.iam.domain.authentication.aggregate.valueobject;

/**
 * 账号/凭据状态
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum UserStatus {
  /** 活跃（可登录） */
  ACTIVE,
  /** 已禁用（管理员停用，不可登录） */
  DISABLED,
  /** 已锁定（登录失败次数超限自动锁定，需管理员解锁） */
  LOCKED
}
