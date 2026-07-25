package com.example.iam.domain.authentication.aggregate.valueobject;

/**
 * 二次授权会话状态
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum SecondaryAuthStatus {
  /** 待完成（已发起，等待经办人验证） */
  PENDING,
  /** 已完成（经办人验证通过，柜员可切换身份） */
  COMPLETED,
  /** 已撤销（柜员或管理员主动撤销） */
  REVOKED,
  /** 已过期（超过有效期自动标记） */
  EXPIRED
}
