package com.pension.permission.application.authorization;


import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;

public record CheckPermissionQuery(
  UserNo identity,
  PlanNo planId,
  BusinessCode businessCode,
  ActionCode actionCode
) {
}
