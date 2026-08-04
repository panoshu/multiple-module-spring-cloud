package com.pension.permission.domain.assignment.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 回答两类查询：
 * 1) 网上渠道"这个账号可以选哪些计划"——正向展开，单账号的分配记录数量小，可以安全枚举
 * 2) 管理台"这个计划下有哪些经办"——反向匹配，复用点对点判定，不管分配总量多大都不用整表展开
 */
@DomainService
@RequiredArgsConstructor
public final class PlanReachabilityService {

  private final AssignmentRepository assignmentRepository;
  private final ProductGateway orgDirectory;

  public List<PlanNo> listSelectablePlans(UserNo accountId) {
    Set<PlanNo> plans = new LinkedHashSet<>();
    for (AgentIdentityAssignment a : assignmentRepository.findActiveByAccount(accountId)) {
      switch (a.scopeDimension()) {
        case PLAN -> plans.add(new PlanNo(a.scopeValue()));
        case CUSTOMER -> plans.addAll(
          orgDirectory.plansOfCustomer(CustomerNo.of(a.scopeValue()), a.isInheritable()));
        case PRODUCT -> plans.addAll(orgDirectory.plansOfProduct(ProductNo.of(a.scopeValue())));
      }
    }
    return List.copyOf(plans);
  }

  public List<AgentIdentityAssignment> listAssignmentsForPlan(PlanNo planId) {
    PlanSnapshot plan = orgDirectory.requirePlan(planId);
    List<CustomerNo> ancestors = orgDirectory.ancestorsOf(plan.customerNo());
    return assignmentRepository.findAllActive().stream()
      .filter(a -> matches(a, plan, ancestors))
      .toList();
  }

  private boolean matches(AgentIdentityAssignment a, PlanSnapshot plan, List<CustomerNo> ancestors) {
    return switch (a.scopeDimension()) {
      case PLAN -> a.scopeValue().equals(plan.planNo().value());
      case PRODUCT -> a.scopeValue().equals(plan.productNo().value());
      case CUSTOMER -> a.isInheritable()
        ? ancestors.stream().anyMatch(c -> c.value().equals(a.scopeValue()))
        : a.scopeValue().equals(plan.customerNo().value());
      case GLOBAL -> false;
    };
  }
}
