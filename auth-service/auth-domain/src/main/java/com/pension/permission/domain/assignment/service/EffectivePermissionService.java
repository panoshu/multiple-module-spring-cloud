package com.pension.permission.domain.assignment.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.*;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.service.AuthorizationEngine;
import com.pension.permission.domain.authorization.service.EffectResolver;
import com.pension.permission.domain.authorization.service.ScopeMatcher;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 权限判定的真正入口，取代应用层直接调用AuthorizationEngine的方式。
 * 主体层校验会合并两类来源：
 * 1) 持久化的Grant(HQ_CONFIG能力层/主体层配置、代办、跨企业委托) —— 走GrantRepository
 * 2) 该身份当前生效的身份分配，实时解析对应的角色模板 —— 不落库，现算现用，
 * 构造成一条"虚拟Grant"(origin=ROLE_TEMPLATE，effect恒为ALLOW，因为模板本身
 * 只表达"拥有哪些权限"，不表达"禁止")一起参与同一套ScopeMatcher+EffectResolver合并。
 * 这样角色模板一旦修改，所有引用它的身份分配立刻在下一次判定时生效，不需要任何
 * "回填已存在Grant"的迁移作业；DENY例外(来自持久化Grant)依然能正确覆盖实时解析出的ALLOW。
 * 这个服务放在assignment包而不是authorization包，是因为它需要同时依赖
 * authorization(GrantRepository)和roletemplate(RoleTemplateResolver)——
 * assignment包本来就合法依赖这两者，放在这里不会引入包间循环依赖。
 */
@DomainService
public final class EffectivePermissionService {

  private final ProductGateway orgDirectory;
  private final GrantRepository grantRepository;
  private final AssignmentRepository assignmentRepository;
  private final RoleTemplateResolver roleTemplateResolver;
  private final PlanMembershipLookup membershipLookup;
  private final AuthorizationEngine authorizationEngine;
  private final ScopeMatcher scopeMatcher;
  private final EffectResolver effectResolver;
  private final IdService idService;

  public EffectivePermissionService(ProductGateway orgDirectory,
                                    GrantRepository grantRepository,
                                    AssignmentRepository assignmentRepository,
                                    RoleTemplateResolver roleTemplateResolver,
                                    PlanMembershipLookup membershipLookup,
                                    AuthorizationEngine authorizationEngine,
                                    IdService idService
  ) {
    this.orgDirectory = orgDirectory;
    this.grantRepository = grantRepository;
    this.assignmentRepository = assignmentRepository;
    this.roleTemplateResolver = roleTemplateResolver;
    this.membershipLookup = membershipLookup;
    this.authorizationEngine = authorizationEngine;
    this.scopeMatcher = new ScopeMatcher(orgDirectory);
    this.effectResolver = new EffectResolver();
    this.idService = idService;
  }

  private static <T> java.util.List<T> concat(java.util.List<T> a, java.util.List<T> b) {
    java.util.List<T> result = new java.util.ArrayList<>(a.size() + b.size());
    result.addAll(a);
    result.addAll(b);
    return result;
  }

  /**
   * 能力层不涉及角色模板，直接复用AuthorizationEngine，不重复实现
   */
  public boolean checkPlanCapability(PlanNo planId, BusinessCode business, LocalDateTime at) {
    return authorizationEngine.checkPlanCapability(planId, business, at);
  }

  public boolean checkSubjectGrant(UserNo identity, PlanNo planId, Permission permission, LocalDateTime at) {
    PlanSnapshot plan = requirePlan(planId);

    List<Grant> persistedMatched = grantRepository.findCandidateSubjectGrants(identity, at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> g.subject().covers(identity, membershipLookup))
      .toList();

    List<Grant> liveMatched = resolveLiveRoleTemplateGrants(identity, at);

    List<Grant> matched = Stream.concat(persistedMatched.stream(), liveMatched.stream())
      .filter(g -> scopeMatcher.matches(g.scopeRules(), plan))
      .filter(g -> g.grants(permission))
      .toList();

    return effectResolver.resolve(matched);
  }

  /**
   * 平台管理权限判定：跳过能力层，主体层用 GLOBAL 规则匹配。
   * 不依赖 planId。仅匹配 scopeRules 为空或全部 GLOBAL 的 Grant。
   */
  public boolean checkPlatformPermission(UserNo identity, Permission permission, LocalDateTime at) {
    List<Grant> persistedMatched = grantRepository.findCandidateSubjectGrants(identity, at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> isGlobalScope(g.scopeRules()))
      .filter(g -> g.subject().covers(identity, membershipLookup))
      .filter(g -> g.grants(permission))
      .toList();

    List<Grant> liveMatched = resolveLiveGlobalRoleTemplateGrants(identity, at);

    List<Grant> matched = Stream.concat(persistedMatched.stream(), liveMatched.stream())
      .filter(g -> g.grants(permission))
      .toList();

    return effectResolver.resolve(matched);
  }

  private boolean isGlobalScope(List<ScopeRule> rules) {
    if (rules.isEmpty()) {
      return true;
    }
    return rules.stream().allMatch(r -> r.dimension() == ScopeDimension.GLOBAL);
  }

  private List<Grant> resolveLiveGlobalRoleTemplateGrants(UserNo identity, LocalDateTime at) {
    return assignmentRepository.findActiveByAccount(identity).stream()
      .filter(a -> a.scopeDimension() == AssignmentScopeDimension.GLOBAL)
      .map(a -> toGlobalVirtualGrant(identity, a, at))
      .toList();
  }

  private Grant toGlobalVirtualGrant(UserNo identity, AgentIdentityAssignment assignment, LocalDateTime at) {
    RoleTemplate template = roleTemplateResolver.resolveOrThrow(
      assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());

    GrantSubject subject = new UserListSubject(Set.of(identity));
    return Grant.create(
      idService.nextId(GrantId.class), identity, subject,
      List.of(),
      template.permissions(), GrantType.BASE,
      GrantOrigin.ROLE_TEMPLATE, Effect.ALLOW, GrantStatus.EFFECTIVE,
      ValidityPeriod.sinceNow(),
      null, null
    );
  }

  /**
   * 最终判定：能力层 AND 主体层
   */
  public boolean checkPermission(UserNo identity, PlanNo planId, Permission permission, LocalDateTime at) {
    if (!checkPlanCapability(planId, permission.businessCode(), at)) {
      return false;
    }
    return checkSubjectGrant(identity, planId, permission, at);
  }

  /**
   * 解析当前用户在指定业务下的可见数据范围.
   *
   * <p>聚合所有 ALLOW/DENY Grant 的 scopeRules：
   * <ul>
   *   <li>GLOBAL ALLOW → globalVisible=true</li>
   *   <li>PLAN ALLOW/DENY → 加入 visiblePlans/excludedPlans</li>
   *   <li>CUSTOMER ALLOW（inheritable=true）→ 加入 visibleCustomers 及其子客户</li>
   *   <li>CUSTOMER DENY → 加入 excludedCustomers</li>
   * </ul>
   *
   * <p>最终 visiblePlans 减去 excludedPlans，visibleCustomers 减去 excludedCustomers。
   *
   * @param identity 用户标识
   * @param business 业务编码
   * @param at       时间点
   * @return 聚合后的可见范围
   */
  public VisibleScope resolveVisibleScope(UserNo identity, BusinessCode business, LocalDateTime at) {
    java.util.List<Grant> persisted = grantRepository.findCandidateSubjectGrants(identity, at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> g.coversBusiness(business))
      .toList();

    java.util.List<Grant> live = resolveLiveRoleTemplateGrants(identity, at).stream()
      .filter(g -> g.coversBusiness(business))
      .toList();

    java.util.Set<String> visiblePlans = new java.util.HashSet<>();
    java.util.Set<String> visibleCustomers = new java.util.HashSet<>();
    java.util.Set<String> deniedPlans = new java.util.HashSet<>();
    java.util.Set<String> deniedCustomers = new java.util.HashSet<>();
    boolean isGlobal = false;

    for (Grant g : concat(persisted, live)) {
      boolean isAllow = g.effect() == Effect.ALLOW;
      for (ScopeRule rule : g.scopeRules()) {
        switch (rule.dimension()) {
          case GLOBAL -> {
            if (isAllow) isGlobal = true;
          }
          case PLAN -> {
            if (isAllow) visiblePlans.add(rule.value());
            else deniedPlans.add(rule.value());
          }
          case CUSTOMER -> {
            if (isAllow) {
              visibleCustomers.add(rule.value());
              if (rule.inheritable()) {
                orgDirectory.descendantsOf(com.example.shared.identifier.id.CustomerNo.of(rule.value()))
                  .forEach(c -> visibleCustomers.add(c.value()));
              }
            } else {
              deniedCustomers.add(rule.value());
            }
          }
          default -> { /* 其他维度暂不参与行级过滤 */ }
        }
      }
    }

    visiblePlans.removeAll(deniedPlans);
    visibleCustomers.removeAll(deniedCustomers);

    if (isGlobal) {
      return VisibleScope.global();
    }
    return new VisibleScope(false, visiblePlans, visibleCustomers, deniedPlans, deniedCustomers);
  }

  /**
   * 把该身份当前活跃的身份分配，实时解析成角色模板对应的权限，
   * 构造成不落库的"虚拟Grant"——字段形态跟真实Grant完全一致，
   * 目的是能直接复用ScopeMatcher/EffectResolver，不需要为这条特殊路径单独写一套合并逻辑。
   */
  public List<Grant> resolveLiveRoleTemplateGrants(UserNo identity, LocalDateTime at) {
    return assignmentRepository.findActiveByAccount(identity).stream()
      .map(assignment -> toVirtualGrant(identity, assignment, at))
      .toList();
  }

  private Grant toVirtualGrant(UserNo identity, AgentIdentityAssignment assignment, LocalDateTime at) {
    RoleTemplate template = roleTemplateResolver.resolveOrThrow(
      assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());

    List<ScopeRule> scopeRules = switch (assignment.scopeDimension()) {
      case PLAN -> List.of(new ScopeRule(ScopeDimension.PLAN, assignment.scopeValue(), assignment.isInheritable()));
      case CUSTOMER ->
        List.of(new ScopeRule(ScopeDimension.CUSTOMER, assignment.scopeValue(), assignment.isInheritable()));
      case PRODUCT ->
        List.of(new ScopeRule(ScopeDimension.PRODUCT, assignment.scopeValue(), assignment.isInheritable()));
      case GLOBAL -> List.of();
    };
    GrantSubject subject = new UserListSubject(Set.of(identity));

    PlanNo planNo = assignment.scopeDimension() == AssignmentScopeDimension.GLOBAL
      ? null : PlanNo.of(assignment.scopeValue());

    return Grant.create(
      idService.nextId(GrantId.class), identity, subject, scopeRules, template.permissions(),
      GrantType.BASE, GrantOrigin.ROLE_TEMPLATE, Effect.ALLOW, GrantStatus.EFFECTIVE,
      ValidityPeriod.sinceNow(), planNo, planNo
    );
  }

  private PlanSnapshot requirePlan(PlanNo planId) {
    return orgDirectory.requirePlan(planId);
  }
}
