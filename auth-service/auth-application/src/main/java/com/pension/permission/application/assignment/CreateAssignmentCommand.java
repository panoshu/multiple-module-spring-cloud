package com.pension.permission.application.assignment;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;

public record CreateAssignmentCommand(
  UserNo accountId,
  RoleCode roleCode,
  AssignmentScopeDimension scopeDimension,
  String scopeValue,
  boolean inheritable,
  UserNo operator
) {
}
