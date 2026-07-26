package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 主体维度(决定权限规则优先级)。
 *
 * <p>权限规则按主体维度分层,层级越高(priority 数值越大)越能覆盖低层级规则:
 * <ul>
 *   <li>{@code CUSTOMER(1)} - 客户级(最低优先级,基础权限)</li>
 *   <li>{@code OPERATION_MODE(2)} - 运作模式级</li>
 *   <li>{@code PRODUCT(3)} - 产品级</li>
 *   <li>{@code PLAN(4)} - 计划级</li>
 *   <li>{@code ACCOUNT_MANAGER(5)} - 账管人级(最高优先级,精确控权)</li>
 * </ul>
 *
 * <p>设计文档 3.5 节:层级顺序在 PermissionCombinationStrategy 中按优先级应用,
 * 高层级规则通过 {@link OverrideMode} 对低层级规则进行扩展(ADD)或收紧(REMOVE)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum SubjectType implements ValueObject {
  /** 客户级(最低优先级) */
  CUSTOMER(1),
  /** 运作模式级 */
  OPERATION_MODE(2),
  /** 产品级 */
  PRODUCT(3),
  /** 计划级 */
  PLAN(4),
  /** 账管人级(最高优先级) */
  ACCOUNT_MANAGER(5);

  private final int priority;

  SubjectType(int priority) {
    this.priority = priority;
  }

  /**
   * 返回主体维度的优先级数值。
   *
   * <p>数值越大优先级越高,高层级规则可覆盖低层级规则。
   *
   * @return 优先级数值(1-5)
   */
  public int priority() {
    return priority;
  }
}
