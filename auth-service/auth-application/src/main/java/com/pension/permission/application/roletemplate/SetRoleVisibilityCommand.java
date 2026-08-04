package com.pension.permission.application.roletemplate;

import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleVisibilityMode;

/**
 * dimension只应传PLAN或CUSTOMER
 */
public record SetRoleVisibilityCommand(
  RoleTemplateScopeDimension dimension,
  String value,
  RoleVisibilityMode mode
) {
}
