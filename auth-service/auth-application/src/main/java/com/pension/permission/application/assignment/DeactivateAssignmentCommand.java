package com.pension.permission.application.assignment;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentId;

public record DeactivateAssignmentCommand(
  AssignmentId assignmentId,
                                          UserNo operator) {
}
