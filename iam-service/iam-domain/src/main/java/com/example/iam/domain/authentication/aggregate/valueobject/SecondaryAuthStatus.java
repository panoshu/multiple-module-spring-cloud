package com.example.iam.domain.authentication.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 二次授权会话状态。
 *
 * <p>状态机(参照设计文档 3.4.3 节):
 * <ul>
 *   <li>{@link #PENDING} - 待授权(柜员发起,等待经办人决策)</li>
 *   <li>{@link #AUTHORIZED} - 已授权(经办人确认,柜员可操作)</li>
 *   <li>{@link #EXPIRED} - 已过期(AUTHORIZED 超时)</li>
 *   <li>{@link #REVOKED} - 已撤销(AUTHORIZED 被撤销)</li>
 *   <li>{@link #CLOSED} - 已关闭(柜员登出)</li>
 *   <li>{@link #REJECTED} - 已拒绝(经办人拒绝)</li>
 * </ul>
 *
 * <p>合法转换:
 * <ul>
 *   <li>PENDING → AUTHORIZED(经办人确认)</li>
 *   <li>PENDING → REJECTED(经办人拒绝)</li>
 *   <li>AUTHORIZED → EXPIRED(超时)</li>
 *   <li>AUTHORIZED → REVOKED(撤销)</li>
 *   <li>AUTHORIZED → CLOSED(柜员登出)</li>
 * </ul>
 *
 * <p>EXPIRED、REVOKED、CLOSED、REJECTED 均为终态,不允许任何转出。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum SecondaryAuthStatus implements ValueObject {
  PENDING,
  AUTHORIZED,
  EXPIRED,
  REVOKED,
  CLOSED,
  REJECTED;

  /**
   * 当前是否为待授权状态。
   *
   * @return 当前为 {@link #PENDING} 时返回 true
   */
  public boolean isPending() {
    return this == PENDING;
  }

  /**
   * 当前是否为已授权状态。
   *
   * @return 当前为 {@link #AUTHORIZED} 时返回 true
   */
  public boolean isAuthorized() {
    return this == AUTHORIZED;
  }

  /**
   * 当前是否为已过期状态。
   *
   * @return 当前为 {@link #EXPIRED} 时返回 true
   */
  public boolean isExpired() {
    return this == EXPIRED;
  }

  /**
   * 当前是否为已撤销状态。
   *
   * @return 当前为 {@link #REVOKED} 时返回 true
   */
  public boolean isRevoked() {
    return this == REVOKED;
  }

  /**
   * 当前是否为已关闭状态。
   *
   * @return 当前为 {@link #CLOSED} 时返回 true
   */
  public boolean isClosed() {
    return this == CLOSED;
  }

  /**
   * 当前是否为已拒绝状态。
   *
   * @return 当前为 {@link #REJECTED} 时返回 true
   */
  public boolean isRejected() {
    return this == REJECTED;
  }

  /**
   * 从当前状态进行经办人决策(转入 AUTHORIZED 或 REJECTED)是否合法。
   *
   * <p>仅 {@link #PENDING} 状态可由经办人决策,确认后转入 {@link #AUTHORIZED},
   * 拒绝后转入 {@link #REJECTED}。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canAuthorize() {
    return this == PENDING;
  }

  /**
   * 从当前状态撤销(转入 REVOKED)是否合法。
   *
   * <p>仅 {@link #AUTHORIZED} → {@link #REVOKED} 合法。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canRevoke() {
    return this == AUTHORIZED;
  }

  /**
   * 从当前状态过期(转入 EXPIRED)是否合法。
   *
   * <p>仅 {@link #AUTHORIZED} → {@link #EXPIRED} 合法。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canExpire() {
    return this == AUTHORIZED;
  }

  /**
   * 从当前状态关闭(转入 CLOSED)是否合法。
   *
   * <p>仅 {@link #AUTHORIZED} → {@link #CLOSED} 合法(柜员登出)。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canClose() {
    return this == AUTHORIZED;
  }
}
