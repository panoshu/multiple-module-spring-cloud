package com.example.iam.domain.authorization.strategy;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCombinationContext;
import com.example.iam.domain.authorization.service.PermissionCombinationStrategy;
import com.example.shared.domain.annotation.DomainService;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优先级覆盖策略 - 默认权限组合策略实现。
 *
 * <p>设计文档 3.6.3 节默认实现,设计文档 3.7 节 PermissionResolver 计算流程步骤 3 算法:
 * <ul>
 *   <li>初始权限集 = 客户级规则授权</li>
 *   <li>逐层应用:高层级规则可 ADD(扩展)或 REMOVE(收紧)</li>
 * </ul>
 *
 * <p>算法步骤:
 * <ol>
 *   <li>将所有规则按 {@link PermissionRule#effectivePriority()} 升序排序(低优先级先应用)</li>
 *   <li>初始化结果集为空</li>
 *   <li>遍历排序后的规则:
 *     <ul>
 *       <li>{@link OverrideMode#ADD} - 将规则权限码加入结果集</li>
 *       <li>{@link OverrideMode#REMOVE} - 将规则权限码从结果集移除(若不存在为 no-op)</li>
 *     </ul>
 *   </li>
 *   <li>将代办权限码合并到结果集(等价于最终的 ADD)</li>
 *   <li>返回不可变集合</li>
 * </ol>
 *
 * <p>设计要点:
 * <ul>
 *   <li>优先级顺序通过 {@link PermissionRule#effectivePriority()} 决定,默认使用
 *       {@link com.example.iam.domain.authorization.aggregate.valueobject.SubjectType#priority()},
 *       可通过规则 priority 字段显式覆盖</li>
 *   <li>同一优先级下,规则按传入顺序应用(稳定排序)</li>
 *   <li>代办权限作为最终步骤合并,不受规则优先级影响</li>
 * </ul>
 *
 * <p>线程安全:本类无状态,可在多线程环境并发调用。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DomainService
public class PriorityOverrideStrategy implements PermissionCombinationStrategy {

  /** 策略名称,对应 YAML 配置 iam.permission.combination-strategy 的 Bean 名称 */
  public static final String STRATEGY_NAME = "priorityOverrideStrategy";

  @Override
  public String name() {
    return STRATEGY_NAME;
  }

  @Override
  public Set<PermissionCode> combine(PermissionCombinationContext context) {
    Objects.requireNonNull(context, "context cannot be null");

    // 步骤 1: 按优先级升序排序(低优先级先应用,高优先级可覆盖)
    // 使用稳定排序(Comparator.comparingInt 默认稳定),保留同优先级规则的原始顺序
    var sortedRules = context.matchedRules().stream()
        .sorted(Comparator.comparingInt(PermissionRule::effectivePriority))
        .collect(Collectors.toList());

    // 步骤 2: 使用 LinkedHashSet 保持插入顺序,便于调试与可预测的迭代顺序
    Set<PermissionCode> result = new LinkedHashSet<>();

    // 步骤 3: 逐条应用规则
    for (PermissionRule rule : sortedRules) {
      Set<PermissionCode> ruleCodes = rule.permissionCodes();
      if (rule.overrideMode() == OverrideMode.ADD) {
        result.addAll(ruleCodes);
      } else {
        result.removeAll(ruleCodes);
      }
    }

    // 步骤 4: 合并代办权限(等价于最终 ADD)
    result.addAll(context.delegationPermissions());

    // 步骤 5: 返回不可变集合
    return Set.copyOf(result);
  }
}
