package com.example.auth.application.assignment;


import com.example.core.types.UserNo;
import com.example.auth.types.AssignmentId;
import com.example.auth.types.RoleCode;

public record ChangeAssignmentRoleCommand(AssignmentId assignmentId, RoleCode newRoleCode, UserNo operator) {
}
