package com.example.auth.domain.assignment.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.AssignmentId;
import com.example.auth.types.RoleCode;

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
