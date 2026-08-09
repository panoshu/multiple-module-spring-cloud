package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.spi.GrantActivationPolicy;

import java.util.Set;

/**
 * 一个可配置的默认实现：按来源(origin)决定是否需要审批。
 * 角色模板生成的Grant(ROLE_TEMPLATE)和总部直接配置的(HQ_CONFIG)默认不需要审批；
 * 跨企业委托(CUSTOMER_TO_AGENT)和计划间代办(PLAN_DELEGATE)默认需要审批，
 * 但这条"默认"本身可以在构造时覆盖，不需要改代码。
 */
public final class DefaultGrantActivationPolicy implements GrantActivationPolicy {

  private final Set<GrantOrigin> originsRequiringApproval;

  public DefaultGrantActivationPolicy() {
    this(Set.of(GrantOrigin.CUSTOMER_TO_AGENT, GrantOrigin.PLAN_DELEGATE));
  }

  public DefaultGrantActivationPolicy(Set<GrantOrigin> originsRequiringApproval) {
    this.originsRequiringApproval = Set.copyOf(originsRequiringApproval);
  }

  @Override
  public boolean requiresApproval(GrantOrigin origin, GrantType grantType) {
    return originsRequiringApproval.contains(origin);
  }
}
