package com.pension.permission.application.authorization;


import com.example.shared.domain.event.EventBus;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.service.DelegationFactory;
import com.pension.permission.types.GrantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 代办/委托关系的用例编排：计划整体代办、计划指定人员代办、跨企业委托，
 * 底层都是DelegationFactory生成的Grant，这一层只负责持久化+事件发布+事务边界。
 * 是否需要审批已经由GrantActivationPolicy在Grant创建时决定好了状态(PENDING_APPROVAL/EFFECTIVE)，
 * 这里不需要再判断一次。
 */
@Service
@RequiredArgsConstructor
public class DelegationApplicationService {

  private final DelegationFactory delegationFactory;
  private final GrantRepository grantRepository;
  private final EventBus eventBus;

  @Transactional
  public GrantId createWholesaleDelegation(CreateWholesaleDelegationCommand command) {
    Grant grant = delegationFactory.delegateWholesale(
      command.sourcePlanId(), command.targetPlanId(), command.permissions(), command.createdBy());
    grantRepository.save(grant);
    grant.domainEvents().forEach(eventBus::publish);
    return grant.id();
  }

  @Transactional
  public List<GrantId> createSelectiveDelegation(CreateSelectiveDelegationCommand command) {
    List<Grant> grants = delegationFactory.delegateSelective(
      command.sourcePlanId(), command.selectedAccounts(), command.targetPlanIds(),
      command.permissions(), command.createdBy());
    grants.forEach(grantRepository::save);
    grants.forEach(g -> g.domainEvents().forEach(eventBus::publish));
    return grants.stream().map(Grant::id).toList();
  }

  @Transactional
  public GrantId createCustomerToAgentDelegation(CreateCustomerToAgentDelegationCommand command) {
    Grant grant = delegationFactory.delegateCustomerToAgent(
      command.servingCustomerId(), command.inheritable(), command.delegatedAccount(),
      command.permissions(), command.createdBy());
    grantRepository.save(grant);
    grant.domainEvents().forEach(eventBus::publish);
    return grant.id();
  }

  // 审批通过/驳回/撤销是所有Grant通用的生命周期操作，统一挂在GrantLifecycleApplicationService，
  // 不放在这里(这里只负责"怎么创建出一条代办/委托关系的Grant")。
}
