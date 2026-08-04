package com.pension.permission.application.authorization;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.Permission;

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
