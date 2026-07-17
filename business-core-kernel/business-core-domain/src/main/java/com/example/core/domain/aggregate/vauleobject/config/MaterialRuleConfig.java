package com.example.core.domain.aggregate.vauleobject.config;

import com.example.core.domain.aggregate.vauleobject.enums.material.RequirementType;
import com.example.core.domain.aggregate.vauleobject.business.BusinessLevel;

/**
 * 材料规则配置
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:12
 */
public record MaterialRuleConfig(
  String materialCode,
  String materialName,
  BusinessLevel businessLevel,
  RequirementType requirementType,
  String activationCondition, // 激活条件 (配置中心专有)
  String materialCondition    // 必传条件 (传入 MaterialItem)
) {
}
