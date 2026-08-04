package com.pension.permission.application.roletemplate;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.types.RoleCode;

import java.util.Set;

public record CreateRoleTemplateCommand(
  RoleCode roleCode,
  RoleTemplateScopeDimension scopeDimension,
  /* GLOBAL维度时传null */
  String scopeValue,
  Set<Permission> permissions,
  UserNo createdBy
) {
}
