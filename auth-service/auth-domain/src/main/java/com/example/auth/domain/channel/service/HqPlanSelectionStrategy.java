package com.example.auth.domain.channel.service;


import com.example.auth.domain.channel.valueobject.AllPlans;
import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.domain.channel.valueobject.SelectablePlanScope;

/**
 * 总部渠道：可选择任何一个计划，不做预筛选，具体能不能办由AuthorizationEngine的两层校验负责
 */
public final class HqPlanSelectionStrategy implements PlanSelectionStrategy {
  @Override
  public SelectablePlanScope listSelectablePlans(EffectiveIdentity identity) {
    return new AllPlans();
  }
}
