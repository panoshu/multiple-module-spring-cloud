package com.example.auth.application.authorization;


import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.Permission;

import java.util.Set;

public record CreateWholesaleDelegationCommand(
  PlanNo sourcePlanId,
  PlanNo targetPlanId,
  Set<Permission> permissions,
  UserNo createdBy
) {
}
