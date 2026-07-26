package com.example.iam.domain.authentication.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 凭据状态。
 *
 * <p>状态机:
 * <ul>
 *   <li>{@link #ACTIVE} - 有效(可校验)</li>
 *   <li>{@link #EXPIRED} - 已过期(自然过期,可撤销)</li>
 *   <li>{@link #REVOKED} - 已撤销(终态,不可恢复)</li>
 * </ul>
 *
 * <p>合法转换:
 * <ul>
 *   <li>ACTIVE → EXPIRED(自然过期)</li>
 *   <li>ACTIVE → REVOKED(主动撤销)</li>
 *   <li>EXPIRED → REVOKED(过期后撤销)</li>
 * </ul>
 *
 * <p>REVOKED 为终态,不允许任何转出。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum CredentialStatus implements ValueObject {
  ACTIVE,
  EXPIRED,
  REVOKED;

  /**
   * 当前是否为有效状态。
   *
   * @return 当前为 {@link #ACTIVE} 时返回 true
   */
  public boolean isActive() {
    return this == ACTIVE;
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
   * 从当前状态标记为 EXPIRED 是否合法。
   *
   * <p>仅 {@link #ACTIVE} → {@link #EXPIRED} 合法。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canMarkExpired() {
    return this == ACTIVE;
  }

  /**
   * 从当前状态撤销(转入 REVOKED)是否合法。
   *
   * <p>合法源状态:ACTIVE、EXPIRED。REVOKED 为终态,不允许重复撤销。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canRevoke() {
    return this == ACTIVE || this == EXPIRED;
  }
}
