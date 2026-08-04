package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.product.ProductSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 点对点匹配：拿一个具体计划去校验它是否满足一组ScopeRule(AND关系)，
 * 而不是反过来把规则展开成计划列表——这样无论系统里计划总量多大，
 * 单次判定的开销只跟"命中的Grant数量"有关，跟计划总数无关(总部渠道"任意计划"场景靠这个撑住)。
 */
public final class ScopeMatcher {

  private final ProductGateway orgDirectory;

  public ScopeMatcher(ProductGateway orgDirectory) {
    this.orgDirectory = orgDirectory;
  }

  public boolean matches(List<ScopeRule> rules, PlanSnapshot plan) {
    return rules.stream().allMatch(rule -> matchesRule(rule, plan));
  }

  private boolean matchesRule(ScopeRule rule, PlanSnapshot plan) {
    return switch (rule.dimension()) {
      case PLAN -> plan.planNo().value().equals(rule.value());
      case PRODUCT -> plan.productNo().value().equals(rule.value());
      case CUSTOMER -> matchesCustomer(rule, plan);
      case ACCOUNT_MANAGER -> product(plan)
        .map(p -> p.accountMgrNo().value().equals(rule.value()))
        .orElse(false);
      case OPERATING_MODE -> product(plan)
        .map(p -> p.operatingMode().name().equals(rule.value()))
        .orElse(false);
    };
  }

  private boolean matchesCustomer(ScopeRule rule, PlanSnapshot plan) {
    if (!rule.inheritable()) {
      return plan.customerNo().value().equals(rule.value());
    }
    // 可继承：规则里的客户是计划所属客户的任意一级祖先(含自身)即算命中
    return orgDirectory.ancestorsOf(plan.customerNo()).stream()
      .anyMatch(c -> c.value().equals(rule.value()));
  }

  private Optional<ProductSnapshot> product(PlanSnapshot plan) {
    return orgDirectory.findProduct(plan.productNo());
  }
}
