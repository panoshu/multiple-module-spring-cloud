package com.pension.permission.domain.role.service;


import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.domain.role.errorcode.RoleError;
import com.pension.permission.domain.role.repository.RoleTemplateRepository;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * 按"最具体优先"的顺序解析角色模板：
 * - 分配锚定在具体计划时：计划级 > 客户级 > 产品级 > 全局
 * - 分配锚定在客户/产品时：不去猜某个具体计划的专属覆盖，只认同级或全局
 * (如果确实需要"客户级管理员在某个计划上有特殊权限"，应该是再加一条计划级的身份分配，
 * 而不是让客户级分配本身变得能感知单个计划的例外——职责边界要清楚)
 */
@DomainService
@RequiredArgsConstructor
public final class RoleTemplateResolver {

  private final RoleTemplateRepository repository;
  private final ProductGateway orgDirectory;

  /**
   * 按"最具体优先"解析角色模板。
   * 返回 Optional，找不到时由调用方决定如何处理。
   * 不再抛出 NoRoleTemplateException。
   */
  public Optional<RoleTemplate> resolve(
    AssignmentScopeDimension dimension,
    String scopeValue,
    RoleCode roleCode
  ) {
    List<RoleTemplate> candidates =
      repository.findByRoleCode(roleCode, RoleTemplateStatus.EFFECTIVE);

    return switch (dimension) {
      case PLAN -> resolveForPlan(new PlanNo(scopeValue), candidates);
      case CUSTOMER -> firstMatch(candidates, RoleTemplateScopeDimension.CUSTOMER, scopeValue)
        .or(() -> firstMatch(candidates, RoleTemplateScopeDimension.GLOBAL, null));
      case PRODUCT -> firstMatch(candidates, RoleTemplateScopeDimension.PRODUCT, scopeValue)
        .or(() -> firstMatch(candidates, RoleTemplateScopeDimension.GLOBAL, null));
    };
  }

  /**
   * 必须解析成功，否则抛出 NoRoleTemplateException。
   * 适用于"角色模板必须存在，不存在即为系统配置错误"的场景。
   */
  public RoleTemplate resolveOrThrow(
    AssignmentScopeDimension dimension,
    String scopeValue,
    RoleCode roleCode
  ) {
    return resolve(dimension, scopeValue, roleCode)
      .orElseThrow(() -> new DomainException(RoleError.ROLE_TEMPLATE_NOT_FOUND));
  }

  private Optional<RoleTemplate> resolveForPlan(
    PlanNo planId,
    List<RoleTemplate> candidates
  ) {
    // 由 Gateway 的 requirePlan 保证计划存在，领域服务不再手动 orElseThrow
    PlanSnapshot plan = orgDirectory.requirePlan(planId);

    return firstMatch(candidates, RoleTemplateScopeDimension.PLAN, planId.value())
      .or(() -> firstMatch(candidates, RoleTemplateScopeDimension.CUSTOMER, plan.customerNo().value()))
      .or(() -> firstMatch(candidates, RoleTemplateScopeDimension.PRODUCT, plan.productNo().value()))
      .or(() -> firstMatch(candidates, RoleTemplateScopeDimension.GLOBAL, null));
  }

  /**
   * 在候选列表中查找第一个匹配指定作用域的模板。
   * 复用 RoleTemplate 聚合根自身的 matchesScope 行为方法。
   */
  private Optional<RoleTemplate> firstMatch(
    List<RoleTemplate> candidates,
    RoleTemplateScopeDimension dim,
    String value
  ) {
    return candidates.stream()
      .filter(t -> t.matchesScope(dim, value))
      .findFirst();
  }
}
