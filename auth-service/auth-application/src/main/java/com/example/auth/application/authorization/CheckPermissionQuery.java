package com.example.auth.application.authorization;


import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.ActionCode;
import com.example.auth.domain.authorization.valueobject.BusinessCode;

public record CheckPermissionQuery(
  UserNo identity,
  PlanNo planId,
  BusinessCode businessCode,
  ActionCode actionCode
) {
}
