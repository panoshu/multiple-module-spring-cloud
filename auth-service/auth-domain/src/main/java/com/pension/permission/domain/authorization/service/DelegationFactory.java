package com.pension.permission.domain.authorization.service;


import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.*;
import com.pension.permission.domain.authorization.spi.GrantActivationPolicy;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.types.GrantId;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 代办/委托关系的便捷工厂：无论是"计划整体代办"、"计划指定人员代办"，
 * 还是"企业主动委托给经办人(可能来自其他企业)"，底层生成的都是同一种Grant，
 * 只是subject/scopeRules/origin的取值不同——这是当初设计决定
 * "代办与跨企业委托都不建新模型，复用Grant"的直接体现。
 * 每个方法内部都会调用一次raiseCreatedEvent()，调用方不需要记得手动补这一步。
 */
@DomainService
@RequiredArgsConstructor
public final class DelegationFactory {

  private final GrantActivationPolicy activationPolicy;
  private final IdService idService;

  /**
   * 计划A整体授权给计划B代办：计划A下的每个经办自动拥有对计划B的授权
   */
  public Grant delegateWholesale(PlanNo sourcePlanId, PlanNo targetPlanId,
                                 Set<Permission> permissions, UserNo createdBy) {
    return build(new PlanAllMembersSubject(sourcePlanId), GrantType.DELEGATE_WHOLESALE,
      GrantOrigin.PLAN_DELEGATE,
      List.of(ScopeRule.of(ScopeDimension.PLAN, targetPlanId.value())),
      sourcePlanId, targetPlanId, permissions, createdBy);
  }

  /**
   * 计划指定人员代办：只有列出的这些经办拥有对目标计划(可多个)的授权
   *
   * @param sourcePlanId
   * @param selectedAccounts
   * @param targetPlanIds
   * @param permissions
   * @param createdBy
   * @return List<Grant>
   */
  public List<Grant> delegateSelective(PlanNo sourcePlanId, Set<UserNo> selectedAccounts,
                                       List<PlanNo> targetPlanIds, Set<Permission> permissions,
                                       UserNo createdBy) {
    return targetPlanIds.stream()
      .map(target -> build(new UserListSubject(selectedAccounts), GrantType.DELEGATE_SELECTIVE,
        GrantOrigin.PLAN_DELEGATE,
        List.of(ScopeRule.of(ScopeDimension.PLAN, target.value())),
        sourcePlanId, target, permissions, createdBy))
      .toList();
  }

  /**
   * 跨企业委托：企业主动将自己名下计划的部分权限委托给经办人(可能来自其他企业)。
   * 发起入口在网上渠道，由该企业下有权限的角色触发；scope锚定在客户维度，
   * inheritable决定是否连带子公司的计划。
   */
  public Grant delegateCustomerToAgent(
    CustomerNo servingCustomerId, boolean inheritable,
    UserNo delegatedAccount, Set<Permission> permissions,
    UserNo createdBy) {
    return build(new UserListSubject(Set.of(delegatedAccount)), GrantType.DELEGATE_SELECTIVE,
      GrantOrigin.CUSTOMER_TO_AGENT,
      List.of(new ScopeRule(ScopeDimension.CUSTOMER, servingCustomerId.value(), inheritable)),
      null, null, permissions, createdBy);
  }

  private Grant build(GrantSubject subject, GrantType grantType, GrantOrigin origin,
                      List<ScopeRule> scopeRules, PlanNo sourcePlanId, PlanNo targetPlanId,
                      Set<Permission> permissions, UserNo createdBy) {
    boolean needsApproval = activationPolicy.requiresApproval(origin, grantType);
    return Grant.create(
      idService.nextId(GrantId.class),
      createdBy,
      subject,
      scopeRules,
      permissions,
      grantType,
      origin,
      Effect.ALLOW,
      needsApproval ? GrantStatus.PENDING_APPROVAL : GrantStatus.EFFECTIVE,
      ValidityPeriod.sinceNow(),
      sourcePlanId,
      targetPlanId
    );
  }
}
