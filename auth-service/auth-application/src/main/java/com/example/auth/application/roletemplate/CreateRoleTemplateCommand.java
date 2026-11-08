package com.example.auth.application.roletemplate;


import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.Permission;
import com.example.auth.domain.role.enumeration.RoleTemplateScopeDimension;
import com.example.auth.types.RoleCode;

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
