package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 代办状态 - PlanDelegation 聚合的生命周期状态。
 *
 * <p>设计文档 3.4 节(PlanDelegation 核心行为):
 * <ul>
 *   <li>{@code ACTIVE} - 活动中:代办关系生效,授权方计划下经办可获得代办授权</li>
 *   <li>{@code REVOKED} - 已撤销:代办关系被撤销,不可恢复</li>
 *   <li>{@code EXPIRED} - 已过期:代办关系到达失效时间,自动过期</li>
 * </ul>
 *
 * <p>状态转换约束:
 * <ul>
 *   <li>仅 {@code ACTIVE} 可转换为 {@code REVOKED}(通过 {@link #canRevoke()} 校验)</li>
 *   <li>仅 {@code ACTIVE} 可转换为 {@code EXPIRED}(通过 {@link #canExpire()} 校验)</li>
 *   <li>{@code REVOKED}/{@code EXPIRED} 为终态,不可恢复</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum DelegationStatus implements ValueObject {
  /** 活动中:代办关系生效 */
  ACTIVE,
  /** 已撤销:代办关系被主动撤销 */
  REVOKED,
  /** 已过期:代办关系到达失效时间 */
  EXPIRED;

  /**
   * 判断当前状态是否为活动状态。
   *
   * @return 当前状态为 ACTIVE 时返回 true
   */
  public boolean isActive() {
    return this == ACTIVE;
  }

  /**
   * 校验当前状态是否允许转换为已撤销(REVOKED)。
   *
   * <p>仅 ACTIVE 状态可被撤销。
   *
   * @return 当前为 ACTIVE 时返回 true
   */
  public boolean canRevoke() {
    return this == ACTIVE;
  }

  /**
   * 校验当前状态是否允许转换为已过期(EXPIRED)。
   *
   * <p>仅 ACTIVE 状态可被标记过期。
   *
   * @return 当前为 ACTIVE 时返回 true
   */
  public boolean canExpire() {
    return this == ACTIVE;
  }
}
