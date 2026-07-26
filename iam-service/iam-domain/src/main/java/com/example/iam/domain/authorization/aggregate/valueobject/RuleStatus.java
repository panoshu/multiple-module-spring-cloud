package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 规则状态 - PermissionRule 聚合的启用/禁用状态。
 *
 * <p>设计文档 3.4 节(PermissionRule 聚合):
 * <ul>
 *   <li>{@code ACTIVE} - 启用:规则参与权限计算</li>
 *   <li>{@code DISABLED} - 禁用:规则被软禁用,不参与权限计算,但可恢复启用</li>
 * </ul>
 *
 * <p>状态转换约束:
 * <ul>
 *   <li>仅 {@code ACTIVE} 可转换为 {@code DISABLED}(通过 {@link #canDisable()} 校验)</li>
 *   <li>仅 {@code DISABLED} 可转换为 {@code ACTIVE}(通过 {@link #canEnable()} 校验)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum RuleStatus implements ValueObject {
  /** 启用:规则参与权限计算 */
  ACTIVE,
  /** 禁用:规则不参与权限计算,可恢复 */
  DISABLED;

  /**
   * 判断当前状态是否为启用状态。
   *
   * @return 当前状态为 ACTIVE 时返回 true
   */
  public boolean isActive() {
    return this == ACTIVE;
  }

  /**
   * 校验当前状态是否允许转换为禁用(DISABLED)。
   *
   * <p>仅 ACTIVE 状态可被禁用。
   *
   * @return 当前为 ACTIVE 时返回 true
   */
  public boolean canDisable() {
    return this == ACTIVE;
  }

  /**
   * 校验当前状态是否允许转换为启用(ACTIVE)。
   *
   * <p>仅 DISABLED 状态可被重新启用。
   *
   * @return 当前为 DISABLED 时返回 true
   */
  public boolean canEnable() {
    return this == DISABLED;
  }
}
