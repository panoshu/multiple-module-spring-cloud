package com.pension.permission.application.authorization;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;

import java.util.List;
import java.util.Set;

/**
 * 配置能力层："符合scopeRules的计划，业务范围是businesses"，effect=DENY时用于例外收紧
 */
public record CreateCapabilityGrantCommand(
  PlanNo planId,
  List<ScopeRule> scopeRules,
  Set<Permission> businesses,
  Effect effect,
  UserNo createdBy
) {
}
