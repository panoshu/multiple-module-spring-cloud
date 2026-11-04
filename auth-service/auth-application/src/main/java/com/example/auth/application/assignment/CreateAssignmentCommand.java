package com.example.auth.application.assignment;


import com.example.core.types.UserNo;
import com.example.auth.types.AssignmentScopeDimension;
import com.example.auth.types.RoleCode;

public record CreateAssignmentCommand(
  UserNo accountId,
  RoleCode roleCode,
  AssignmentScopeDimension scopeDimension,
  String scopeValue,
  boolean inheritable,
  UserNo operator
) {
}
