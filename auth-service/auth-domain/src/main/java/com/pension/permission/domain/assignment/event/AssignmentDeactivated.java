package com.pension.permission.domain.assignment.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.AssignmentId;

import java.time.LocalDateTime;

/**
 * 经办离职/停用：应用层监听后应联动撤销派生的Grant并立即淘汰快照(已经由GrantProvisioningService触发)。
 */
public record AssignmentDeactivated(
  AssignmentId assignmentId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {
  public static AssignmentDeactivated of(AssignmentId assignmentId, UserNo createdBy) {
    return new AssignmentDeactivated(assignmentId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
