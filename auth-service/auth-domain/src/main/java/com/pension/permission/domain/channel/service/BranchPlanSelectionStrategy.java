package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;

/**
 * 网点渠道：柜员自己不能直接选计划，必须先完成二次授权(见SecondaryAuthService)
 * 换取被授权经办的EffectiveIdentity，之后直接委托给网上渠道策略——
 * 网点场景不需要单独实现一套"可选计划"的逻辑。
 */
public final class BranchPlanSelectionStrategy implements PlanSelectionStrategy {

  private final OnlinePlanSelectionStrategy delegate;

  public BranchPlanSelectionStrategy(OnlinePlanSelectionStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public SelectablePlanScope listSelectablePlans(EffectiveIdentity identity) {
    if (!identity.viaSecondaryAuth()) {
      throw new IllegalStateException("网点渠道必须先完成经办二次授权才能选择计划");
    }
    return delegate.listSelectablePlans(identity);
  }
}
