package com.pension.permission.domain.channel.enumeration;

/**
 * 二次授权会话状态机.
 *
 * <pre>
 *                                  ┌──────────────┐
 *                                  │   PENDING    │ ◄── 柜员发起
 *                                  └──────┬───────┘
 *              ┌──────────────────────────┼──────────────────────────┐
 *              ▼                          ▼                          ▼
 *      ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
 *      │  AUTHORIZED  │          │   REJECTED   │          │   EXPIRED    │
 *      └──────┬───────┘          └──────────────┘          └──────────────┘
 *      ┌──────┴──────────────────┐
 *      ▼                         ▼
 * ┌──────────────┐         ┌──────────────┐
 * │   REVOKED    │         │   CLOSED     │
 * └──────────────┘         └──────────────┘
 * </pre>
 */
public enum SecondaryAuthStatus {
  PENDING,
  AUTHORIZED,
  REJECTED,
  EXPIRED,
  REVOKED,
  CLOSED;

  /**
   * 判断是否为终态.
   */
  public boolean isTerminal() {
    return this == REJECTED || this == EXPIRED || this == REVOKED || this == CLOSED;
  }

  /**
   * 判断是否为活跃态（可继续流转）.
   */
  public boolean isActive() {
    return this == PENDING || this == AUTHORIZED;
  }
}
