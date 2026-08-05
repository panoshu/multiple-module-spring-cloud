package com.pension.permission.domain.assignment.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.errorcode.RoleError;
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
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
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
   * 最终判定：能力层 AND 主体层
   */
  public boolean checkPermission(UserNo identity, PlanNo planId, Permission permission, LocalDateTime at) {
    if (!checkPlanCapability(planId, permission.businessCode(), at)) {
      return false;
    }
    return checkSubjectGrant(identity, planId, permission, at);
  }

  /**
   * 把该身份当前活跃的身份分配，实时解析成角色模板对应的权限，
   * 构造成不落库的"虚拟Grant"——字段形态跟真实Grant完全一致，
   * 目的是能直接复用ScopeMatcher/EffectResolver，不需要为这条特殊路径单独写一套合并逻辑。
   */
  private List<Grant> resolveLiveRoleTemplateGrants(UserNo identity, LocalDateTime at) {
    return assignmentRepository.findActiveByAccount(identity).stream()
      .map(assignment -> toVirtualGrant(identity, assignment, at))
      .toList();
  }

  private Grant toVirtualGrant(UserNo identity, AgentIdentityAssignment assignment, LocalDateTime at) {
    RoleTemplate template = roleTemplateResolver.resolveOrThrow(
      assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());

    ScopeDimension dimension = switch (assignment.scopeDimension()) {
      case PLAN -> ScopeDimension.PLAN;
      case CUSTOMER -> ScopeDimension.CUSTOMER;
      case PRODUCT -> ScopeDimension.PRODUCT;
      case GLOBAL -> throw new DomainException(RoleError.UNSUPPORTED_SCOPE_DIMENSION);
    };
    ScopeRule scopeRule = new ScopeRule(dimension, assignment.scopeValue(), assignment.isInheritable());
    GrantSubject subject = new UserListSubject(Set.of(identity));

    return Grant.create(
      idService.nextId(GrantId.class), identity, subject, List.of(scopeRule), template.permissions(), GrantType.BASE,
      GrantOrigin.ROLE_TEMPLATE, Effect.ALLOW, GrantStatus.EFFECTIVE, ValidityPeriod.sinceNow(),
      // 这里的传参需要审核
      PlanNo.of(assignment.scopeValue()), PlanNo.of(assignment.scopeValue())
    );
  }

  private PlanSnapshot requirePlan(PlanNo planId) {
    return orgDirectory.requirePlan(planId);
  }
}
