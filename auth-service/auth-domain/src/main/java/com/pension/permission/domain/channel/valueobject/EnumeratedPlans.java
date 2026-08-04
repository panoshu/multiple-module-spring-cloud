package com.pension.permission.domain.channel.valueobject;

import com.example.shared.identifier.id.PlanNo;

import java.util.List;

/**
 * 网上渠道(及网点二次授权后)用：明确枚举出的可选计划列表
 */
public record EnumeratedPlans(List<PlanNo> plans) implements SelectablePlanScope {
}
