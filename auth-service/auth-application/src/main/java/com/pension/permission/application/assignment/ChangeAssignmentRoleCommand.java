package com.pension.permission.application.assignment;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.RoleCode;

public record ChangeAssignmentRoleCommand(AssignmentId assignmentId, RoleCode newRoleCode, UserNo operator) {
}
