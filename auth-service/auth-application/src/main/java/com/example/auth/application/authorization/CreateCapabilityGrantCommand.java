package com.example.auth.application.authorization;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.enumeration.Effect;
import com.example.auth.domain.authorization.valueobject.Permission;
import com.example.auth.domain.authorization.valueobject.ScopeRule;

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
