package com.pension.permission.domain.role.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleVisibilityMode;
import com.pension.permission.domain.role.repository.RoleTemplateRepository;
import com.pension.permission.domain.role.repository.RoleVisibilityRepository;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 回答"给这个计划分配经办时，下拉框里该展示哪些角色"——
 * 默认展示全部(通用+专属)；如果这个计划/客户被配置成"仅展示专属角色"，
 * 就按角色模板解析到的"来源层级"过滤掉全局/其他层级的通用角色，
 * 而不是靠维护一份要屏蔽的角色黑名单——新增全局角色时不需要任何人为这个客户做额外配置。
 */
@DomainService
@RequiredArgsConstructor
public final class RoleVisibilityResolver {

  private final RoleVisibilityRepository visibilityRepository;
  private final RoleTemplateRepository roleTemplateRepository;
  private final RoleTemplateResolver roleTemplateResolver;
  private final ProductGateway orgDirectory;

  /**
   * 解析指定计划的可见性模式：计划级配置 > 客户级配置 > 默认 SHOW_ALL
   */
  public RoleVisibilityMode resolveMode(PlanNo planId) {
    return visibilityRepository.findByPlan(planId)
      .map(RoleVisibilityScope::mode)
      .orElseGet(() -> {
        // 由 Gateway 的 requirePlan 保证计划存在
        PlanSnapshot plan = orgDirectory.requirePlan(planId);
        return visibilityRepository.findByCustomer(plan.customerNo())
          .map(RoleVisibilityScope::mode)
          .orElse(RoleVisibilityMode.SHOW_ALL);
      });
  }

  /**
   * 列出指定计划下可分配的角色列表
   */
  public List<RoleCode> listSelectableRoles(PlanNo planId) {
    RoleVisibilityMode mode = resolveMode(planId);
    PlanSnapshot plan = orgDirectory.requirePlan(planId);

    List<RoleCode> result = new ArrayList<>();

    for (RoleCode code : roleTemplateRepository.findAllRoleCodes()) {
      // resolve 现在返回 Optional，无需 try-catch
      Optional<RoleTemplate> templateOpt =
        roleTemplateResolver.resolve(AssignmentScopeDimension.PLAN, planId.value(), code);

      if (templateOpt.isEmpty()) {
        continue;
      }

      if (mode == RoleVisibilityMode.EXCLUSIVE_ONLY && !belongsToScope(templateOpt.get(), planId, plan)) {
        continue;
      }

      result.add(code);
    }

    return result;
  }

  /**
   * 判断模板是否"属于"当前计划的作用域（计划级或客户级专属）
   */
  private boolean belongsToScope(RoleTemplate template, PlanNo planId, PlanSnapshot plan) {
    if (template.isGlobal()) {
      return false;
    }
    boolean isPlanLevel =
      template.scopeDimension() == RoleTemplateScopeDimension.PLAN
        && Objects.equals(template.scopeValue(), planId.value());
    boolean isCustomerLevel =
      template.scopeDimension() == RoleTemplateScopeDimension.CUSTOMER
        && Objects.equals(template.scopeValue(), plan.customerNo().value());
    return isPlanLevel || isCustomerLevel;
  }
}
