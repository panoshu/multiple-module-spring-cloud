package com.example.auth.application.roletemplate;

import com.example.auth.domain.role.enumeration.RoleTemplateScopeDimension;
import com.example.auth.domain.role.enumeration.RoleVisibilityMode;

/**
 * dimension只应传PLAN或CUSTOMER
 */
public record SetRoleVisibilityCommand(
  RoleTemplateScopeDimension dimension,
  String value,
  RoleVisibilityMode mode
) {
}
