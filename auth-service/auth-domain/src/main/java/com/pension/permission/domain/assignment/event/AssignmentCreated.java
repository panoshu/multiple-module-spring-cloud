package com.pension.permission.domain.assignment.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentId;

import java.time.LocalDateTime;

public record AssignmentCreated(
  AssignmentId assignmentId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {
  public static AssignmentCreated of(AssignmentId assignmentId, UserNo createdBy) {
    return new AssignmentCreated(assignmentId, EventId.generate(), LocalDateTime.now(), null);
  }
}
