package com.example.auth.application.assignment;


import com.example.core.types.UserNo;
import com.example.auth.types.AssignmentId;

public record DeactivateAssignmentCommand(
  AssignmentId assignmentId,
  UserNo operator) {
}
