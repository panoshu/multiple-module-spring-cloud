package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;

public final class OnlinePlanSelectionStrategy implements PlanSelectionStrategy {

  private final PlanReachabilityService planReachabilityService;

  public OnlinePlanSelectionStrategy(PlanReachabilityService planReachabilityService) {
    this.planReachabilityService = planReachabilityService;
  }

  @Override
  public SelectablePlanScope listSelectablePlans(EffectiveIdentity identity) {
    return new EnumeratedPlans(planReachabilityService.listSelectablePlans(identity.identityAccountId()));
  }
}
