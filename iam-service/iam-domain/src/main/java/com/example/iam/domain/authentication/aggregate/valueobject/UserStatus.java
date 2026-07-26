package com.example.iam.domain.authentication.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 用户账号状态。
 *
 * <p>状态机:
 * <ul>
 *   <li>{@link #ACTIVE} - 正常(可登录)</li>
 *   <li>{@link #DISABLED} - 已禁用(管理员停用)</li>
 *   <li>{@link #LOCKED} - 已锁定(登录失败次数超限)</li>
 * </ul>
 *
 * <p>合法转换:
 * <ul>
 *   <li>ACTIVE → DISABLED(管理员禁用)</li>
 *   <li>ACTIVE → LOCKED(系统锁定)</li>
 *   <li>LOCKED → DISABLED(管理员禁用已锁定账号)</li>
 *   <li>LOCKED → ACTIVE(自动解锁或管理员解锁)</li>
 *   <li>DISABLED → ACTIVE(管理员启用)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum UserStatus implements ValueObject {
  ACTIVE,
  DISABLED,
  LOCKED;

  /**
   * 当前是否为正常状态。
   *
   * @return 当前为 {@link #ACTIVE} 时返回 true
   */
  public boolean isActive() {
    return this == ACTIVE;
  }

  /**
   * 当前是否为禁用状态。
   *
   * @return 当前为 {@link #DISABLED} 时返回 true
   */
  public boolean isDisabled() {
    return this == DISABLED;
  }

  /**
   * 当前是否为锁定状态。
   *
   * @return 当前为 {@link #LOCKED} 时返回 true
   */
  public boolean isLocked() {
    return this == LOCKED;
  }

  /**
   * 从当前状态转入 DISABLED 是否合法。
   *
   * <p>合法源状态:ACTIVE、LOCKED。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canDisable() {
    return this == ACTIVE || this == LOCKED;
  }

  /**
   * 从当前状态转入 ACTIVE 是否合法。
   *
   * <p>合法源状态:DISABLED、LOCKED。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canEnable() {
    return this == DISABLED || this == LOCKED;
  }

  /**
   * 从当前状态转入 LOCKED 是否合法。
   *
   * <p>合法源状态:ACTIVE(仅活跃账号可被锁定,禁用账号不会被锁定)。
   *
   * @return 合法转换返回 true,否则 false
   */
  public boolean canLock() {
    return this == ACTIVE;
  }
}
