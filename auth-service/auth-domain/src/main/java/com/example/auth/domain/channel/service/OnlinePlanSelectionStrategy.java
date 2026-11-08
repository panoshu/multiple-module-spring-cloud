package com.example.auth.domain.channel.service;

import com.example.auth.domain.assignment.service.PlanReachabilityService;
import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.domain.channel.valueobject.EnumeratedPlans;
import com.example.auth.domain.channel.valueobject.SelectablePlanScope;

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
