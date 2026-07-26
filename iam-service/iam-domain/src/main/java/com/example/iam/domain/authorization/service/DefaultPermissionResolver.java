package com.example.iam.domain.authorization.service;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCombinationContext;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionMatchContext;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.domain.authorization.gateway.PlanMetadataGateway;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.types.UserId;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 默认权限解析器实现 - 协调防腐层、仓储与组合策略,计算用户在计划上下文下的权限快照。
 *
 * <p>设计文档 3.7 节 PermissionResolver 计算流程:
 * <ol>
 *   <li>通过 {@link PlanMetadataGateway} 加载计划元数据</li>
 *   <li>通过 {@link PermissionRuleRepository} 加载所有适用维度的活动规则</li>
 *   <li>通过 {@link PlanDelegationRepository} 加载被授权方为当前计划的活动代办关系</li>
 *   <li>过滤代办关系对当前用户的授权,合并代办权限码</li>
 *   <li>构建 {@link PermissionCombinationContext},调用 {@link PermissionCombinationStrategy#combine}</li>
 *   <li>输出 {@link PermissionSnapshot}(含权限集合 + 计算时间戳)</li>
 * </ol>
 *
 * <p>设计要点:
 * <ul>
 *   <li>本类为无状态领域服务,通过构造函数注入依赖</li>
 *   <li>组合策略通过构造函数注入,允许在 infrastructure 层通过 Spring 配置切换</li>
 *   <li>不缓存计算结果,缓存策略由调用方决定</li>
 *   <li>计划不存在时抛 {@link BusinessException}(错误码 SERVICE.IAM.0180)</li>
 * </ul>
 *
 * <p>线程安全:依赖均为无状态或线程安全接口,本类可在多线程环境并发调用。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DomainService
public class DefaultPermissionResolver implements PermissionResolver {

  private final PlanMetadataGateway planMetadataGateway;
  private final PermissionRuleRepository permissionRuleRepository;
  private final PlanDelegationRepository planDelegationRepository;
  private final PermissionCombinationStrategy combinationStrategy;

  /**
   * 构造函数注入依赖。
   *
   * @param planMetadataGateway      计划元数据防腐层网关
   * @param permissionRuleRepository 权限规则仓储
   * @param planDelegationRepository 计划代办关系仓储
   * @param combinationStrategy      权限组合策略
   */
  public DefaultPermissionResolver(PlanMetadataGateway planMetadataGateway,
                                    PermissionRuleRepository permissionRuleRepository,
                                    PlanDelegationRepository planDelegationRepository,
                                    PermissionCombinationStrategy combinationStrategy) {
    this.planMetadataGateway = Objects.requireNonNull(planMetadataGateway,
        "planMetadataGateway cannot be null");
    this.permissionRuleRepository = Objects.requireNonNull(permissionRuleRepository,
        "permissionRuleRepository cannot be null");
    this.planDelegationRepository = Objects.requireNonNull(planDelegationRepository,
        "planDelegationRepository cannot be null");
    this.combinationStrategy = Objects.requireNonNull(combinationStrategy,
        "combinationStrategy cannot be null");
  }

  @Override
  public PermissionSnapshot resolve(UserId userId, String planNo) {
    Objects.requireNonNull(userId, "userId cannot be null");
    if (planNo == null || planNo.isBlank()) {
      throw new BusinessException(IamAuthzErrorCode.PLAN_NOT_FOUND)
          .withUserDetail("计划编号不能为空");
    }

    // 步骤 1: 加载计划元数据(防腐层调用外部系统)
    PlanMetadata planMetadata = planMetadataGateway.findByPlanNo(planNo)
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.PLAN_NOT_FOUND)
            .withUserDetail("计划不存在: " + planNo)
            .withContext("planNo", planNo));

    // 步骤 2: 构建匹配上下文
    PermissionMatchContext matchContext = PermissionMatchContext.from(planMetadata);

    // 步骤 3: 加载所有适用维度的活动规则
    List<PermissionRule> matchedRules = loadEffectiveRules(matchContext);

    // 步骤 4: 加载代办关系并合并代办权限码
    Set<PermissionCode> delegationPermissions = loadDelegationPermissions(planNo, userId);

    // 步骤 5: 构建组合上下文,调用组合策略计算最终权限
    PermissionCombinationContext context = PermissionCombinationContext.of(
        matchedRules, delegationPermissions, matchContext);
    Set<PermissionCode> finalPermissions = combinationStrategy.combine(context);

    // 步骤 6: 输出权限快照
    return new PermissionSnapshot(userId, planNo, finalPermissions, LocalDateTime.now());
  }

  /**
   * 加载所有适用维度的活动规则。
   *
   * <p>使用仓储的复合查询方法一次返回五个维度对应的所有活动规则,
   * 仓储实现负责过滤 status=ACTIVE 与生效时间区间。
   *
   * @param matchContext 匹配上下文
   * @return 活动规则列表(可能为空)
   */
  private List<PermissionRule> loadEffectiveRules(PermissionMatchContext matchContext) {
    return permissionRuleRepository.findEffectiveRulesForContext(
        matchContext.customerNo(),
        matchContext.operationMode().name(),
        matchContext.productNo(),
        matchContext.planNo(),
        matchContext.accountManagerCode()
    );
  }

  /**
   * 加载当前用户可获得的代办权限码集合。
   *
   * <p>步骤:
   * <ol>
   *   <li>查询被授权方为当前计划的所有活动代办关系</li>
   *   <li>过滤代办关系对当前用户的授权({@link PlanDelegation#authorizes(Long)})</li>
   *   <li>合并所有授权代办关系的权限码</li>
   * </ol>
   *
   * @param planNo 计划编号
   * @param userId 用户标识
   * @return 代办权限码集合(可能为空)
   */
  private Set<PermissionCode> loadDelegationPermissions(String planNo, UserId userId) {
    List<PlanDelegation> delegations = planDelegationRepository.findEffectiveByDelegatee(planNo);
    if (delegations.isEmpty()) {
      return Set.of();
    }
    Long operatorId = userId.value();
    Set<PermissionCode> permissions = new HashSet<>();
    for (PlanDelegation delegation : delegations) {
      if (delegation.authorizes(operatorId)) {
        permissions.addAll(delegation.permissionCodesFor(operatorId));
      }
    }
    return Set.copyOf(permissions);
  }
}
