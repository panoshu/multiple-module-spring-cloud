package com.example.auth.application.authorization;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.Permission;

import java.util.List;
import java.util.Set;

public record CreateSelectiveDelegationCommand(
  PlanNo sourcePlanId,
  Set<UserNo> selectedAccounts,
  List<PlanNo> targetPlanIds,
  Set<Permission> permissions,
  UserNo createdBy
) {
}
