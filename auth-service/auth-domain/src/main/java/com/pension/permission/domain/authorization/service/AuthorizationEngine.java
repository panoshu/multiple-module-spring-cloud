package com.pension.permission.domain.authorization.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限判定的核心门面：两层AND校验——先看计划本身有没有这个业务的能力(能力层)，
 * 再看这个身份有没有被授权(主体层)，两层都命中才放行。
 */
@DomainService
public final class AuthorizationEngine {

  private final ProductGateway orgDirectory;
  private final GrantRepository grantRepository;
  private final PlanMembershipLookup membershipLookup;
  private final ScopeMatcher scopeMatcher;
  private final EffectResolver effectResolver;

  public AuthorizationEngine(ProductGateway orgDirectory,
                             GrantRepository grantRepository,
                             PlanMembershipLookup membershipLookup) {
    this.orgDirectory = orgDirectory;
    this.grantRepository = grantRepository;
    this.membershipLookup = membershipLookup;
    this.scopeMatcher = new ScopeMatcher(orgDirectory);
    this.effectResolver = new EffectResolver();
  }

  public boolean checkPlanCapability(PlanNo planId, BusinessCode business, LocalDateTime at) {
    PlanSnapshot plan = requirePlan(planId);
    List<Grant> matched = grantRepository.findActiveCapabilityGrants(at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> scopeMatcher.matches(g.scopeRules(), plan))
      .filter(g -> g.coversBusiness(business))
      .toList();
    return effectResolver.resolve(matched);
  }

  public boolean checkSubjectGrant(UserNo identity, PlanNo planId, Permission permission, LocalDateTime at) {
    PlanSnapshot plan = requirePlan(planId);
    List<Grant> matched = grantRepository.findCandidateSubjectGrants(identity, at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> g.subject().covers(identity, membershipLookup))
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

  private PlanSnapshot requirePlan(PlanNo planId) {
    return orgDirectory.requirePlan(planId);
  }
}
