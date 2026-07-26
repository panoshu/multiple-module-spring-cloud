package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Objects;

/**
 * 业务动作明细 - {@link com.example.iam.domain.authorization.aggregate.root.BusinessDefinition}
 * 聚合内值对象,声明某业务支持的动作及其描述。
 *
 * <p>设计文档 6.8 节初始化数据:
 * <ul>
 *   <li>ANNUITY_ESTABLISH: HANDLE/QUERY/AUDIT</li>
 *   <li>ANNUITY_CONTRIBUTION: HANDLE/QUERY</li>
 *   <li>ANNUITY_PAYMENT: HANDLE/QUERY/AUDIT</li>
 * </ul>
 *
 * <p>本值对象由 BusinessDefinition 聚合通过 {@code supportedActions} 集合持有,
 * 用于校验 PermissionRule 的 allowedActions 是否在业务定义支持的动作范围内。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record BusinessAction(
    Action action,
    String description
) implements ValueObject {

  public BusinessAction {
    Objects.requireNonNull(action, "action cannot be null");
  }

  /**
   * 静态工厂方法。
   *
   * @param action       业务动作枚举
   * @param description  动作描述(可空)
   * @return 业务动作明细值对象
   */
  public static BusinessAction of(Action action, String description) {
    return new BusinessAction(action, description);
  }

  /**
   * 仅指定动作创建(无描述)。
   *
   * @param action 业务动作枚举
   * @return 业务动作明细值对象
   */
  public static BusinessAction of(Action action) {
    return new BusinessAction(action, null);
  }
}
