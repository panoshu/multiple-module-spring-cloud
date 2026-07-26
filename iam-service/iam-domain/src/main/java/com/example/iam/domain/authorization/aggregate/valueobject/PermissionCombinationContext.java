package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 权限组合上下文 - {@link com.example.iam.domain.authorization.service.PermissionCombinationStrategy}
 * 的输入,封装权限计算流程中已加载的规则与代办授权。
 *
 * <p>设计文档 3.7 节 PermissionResolver 计算流程步骤 5:
 * 将步骤 2 加载的活动规则集合与步骤 4 加载的代办权限码集合传入组合策略,
 * 由策略实现按优先级算法计算最终权限码集合。
 *
 * <p>字段说明:
 * <ul>
 *   <li>{@code matchedRules} - 匹配上下文命中的活动规则列表(调用方负责已按优先级排序或不排序,
 *       具体由策略实现决定排序方式)</li>
 *   <li>{@code delegationPermissions} - 当前用户可获得的代办授权权限码集合(已合并所有生效的代办关系)</li>
 *   <li>{@code matchContext} - 原始匹配上下文(供策略实现参考维度信息,可选)</li>
 * </ul>
 *
 * <p>不变量:
 * <ul>
 *   <li>所有字段非 null(违反抛 {@link NullPointerException})</li>
 *   <li>{@code matchedRules} 与 {@code delegationPermissions} 通过不可变包装封装</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionCombinationContext(
    List<PermissionRule> matchedRules,
    Set<PermissionCode> delegationPermissions,
    PermissionMatchContext matchContext
) implements ValueObject {

  public PermissionCombinationContext {
    Objects.requireNonNull(matchedRules, "matchedRules cannot be null");
    Objects.requireNonNull(delegationPermissions, "delegationPermissions cannot be null");
    Objects.requireNonNull(matchContext, "matchContext cannot be null");
    matchedRules = List.copyOf(matchedRules);
    delegationPermissions = Set.copyOf(delegationPermissions);
  }

  /**
   * 静态工厂方法。
   *
   * @param matchedRules          匹配规则列表
   * @param delegationPermissions 代办授权权限码集合
   * @param matchContext          原始匹配上下文
   * @return 组合上下文
   */
  public static PermissionCombinationContext of(List<PermissionRule> matchedRules,
                                                 Set<PermissionCode> delegationPermissions,
                                                 PermissionMatchContext matchContext) {
    return new PermissionCombinationContext(matchedRules, delegationPermissions, matchContext);
  }

  /**
   * 创建空上下文(无规则、无代办权限)。
   *
   * @param matchContext 匹配上下文
   * @return 空组合上下文
   */
  public static PermissionCombinationContext empty(PermissionMatchContext matchContext) {
    return new PermissionCombinationContext(List.of(), Set.of(), matchContext);
  }
}
