package com.pension.permission.domain.assignment.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.RoleCode;

import java.time.LocalDateTime;

public record AssignmentRoleChanged(
  AssignmentId assignmentId,
  RoleCode newRoleCode,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {
  public static AssignmentRoleChanged of(
    AssignmentId assignmentId,
    RoleCode newRoleCode,
    UserNo createdBy
  ) {
    return new AssignmentRoleChanged(
      assignmentId,
      newRoleCode,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
