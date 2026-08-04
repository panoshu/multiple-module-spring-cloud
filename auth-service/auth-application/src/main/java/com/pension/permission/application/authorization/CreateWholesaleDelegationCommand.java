package com.pension.permission.application.authorization;


import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.Permission;

import java.util.Set;

public record CreateWholesaleDelegationCommand(
  PlanNo sourcePlanId,
  PlanNo targetPlanId,
  Set<Permission> permissions,
  UserNo createdBy
) {
}
