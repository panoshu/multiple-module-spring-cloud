package com.pension.permission.domain.authorization.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.spi.GrantActivationPolicy;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.CapabilitySubject;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.types.GrantId;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 总部直接配置类Grant的工厂，覆盖两种场景：
 * 1) 能力层：定义某个计划/产品/客户/账管人/运作模式范围内，哪些业务是"开通"的天花板
 * (对应"计划A可以办理业务1、2、3"、"产品P默认开1234，计划A禁用4"这类需求)
 * 2) 主体层的总部直接授权：给具体账号(运营人员/经办)在某个范围内的个别授权或DENY例外
 * (对应"小王额外有业务9的查询权限"、"小李业务3不能审核"这类需求)
 * 两种都是 origin=HQ_CONFIG，默认不需要审批(见DefaultGrantActivationPolicy)。
 */
@DomainService
@RequiredArgsConstructor
public final class GrantConfigurationFactory {

  private final GrantActivationPolicy activationPolicy;
  private final IdService idService;

  public Grant createCapabilityGrant(List<ScopeRule> scopeRules, Set<Permission> businesses,
                                     Effect effect, UserNo createdBy) {

    return build(new CapabilitySubject(), scopeRules, businesses, effect, createdBy);
  }

  public Grant createSubjectGrant(Set<UserNo> accountIds, List<ScopeRule> scopeRules,
                                  Set<Permission> permissions, Effect effect, UserNo createdBy) {

    return build(new UserListSubject(accountIds), scopeRules, permissions, effect, createdBy);
  }

  private Grant build(GrantSubject subject,
                      List<ScopeRule> scopeRules, Set<Permission> permissions, Effect effect,
                      UserNo createdBy) {
    boolean needsApproval = activationPolicy.requiresApproval(GrantOrigin.HQ_CONFIG, GrantType.BASE);

    return Grant.create(
      idService.nextId(GrantId.class),
      createdBy,
      subject,
      scopeRules,
      permissions,
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      effect,
      needsApproval ? GrantStatus.PENDING_APPROVAL : GrantStatus.EFFECTIVE,
      ValidityPeriod.sinceNow(),
      null,
      null
    );
  }
}
