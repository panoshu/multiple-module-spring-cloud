package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Objects;

/**
 * 代办授权权限 - PlanDelegation 聚合内的值对象,声明被授权方获得的某业务某动作。
 *
 * <p>设计文档 3.4.5 节:PlanDelegation 通过 {@code delegatedPermissions} 集合声明授权范围,
 * 每条记录由 {@code businessCode + action} 唯一标识。
 *
 * <p>PermissionResolver 计算代办授权时,将本值对象转换为 {@link PermissionCode}
 * (格式 {@code businessCode.action}),合并到用户最终权限集合。
 *
 * <p>不变量:
 * <ul>
 *   <li>businessCode 非 null</li>
 *   <li>action 非 null</li>
 * </ul>
 *
 * <p>同一 PlanDelegation 内的 (businessCode, action) 组合唯一,由 PlanDelegation 聚合根保证。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record DelegationPermission(
    BusinessCode businessCode,
    Action action
) implements ValueObject {

  public DelegationPermission {
    Objects.requireNonNull(businessCode, "businessCode cannot be null");
    Objects.requireNonNull(action, "action cannot be null");
  }

  /**
   * 静态工厂方法。
   *
   * @param businessCode 业务编码
   * @param action       业务动作
   * @return 代办授权权限值对象
   */
  public static DelegationPermission of(BusinessCode businessCode, Action action) {
    return new DelegationPermission(businessCode, action);
  }

  /**
   * 转换为权限码。
   *
   * @return 权限码(如 "ANNUITY_ESTABLISH.HANDLE")
   */
  public PermissionCode toPermissionCode() {
    return businessCode.toPermissionCode(action);
  }

  /**
   * 判断是否匹配指定业务编码和动作。
   *
   * @param businessCode 业务编码
   * @param action       业务动作
   * @return 匹配返回 true
   */
  public boolean matches(BusinessCode businessCode, Action action) {
    return this.businessCode.equals(businessCode) && this.action == action;
  }
}
