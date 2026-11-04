package com.example.auth.domain.channel.service;


import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.domain.channel.valueobject.SelectablePlanScope;

/**
 * 每个渠道一个实现，决定"可选计划范围"这一渠道差异化逻辑(策略模式)。
 */
public interface PlanSelectionStrategy {
  SelectablePlanScope listSelectablePlans(EffectiveIdentity identity);
}
