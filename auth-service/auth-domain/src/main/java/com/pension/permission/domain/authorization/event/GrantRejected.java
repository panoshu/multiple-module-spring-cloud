package com.pension.permission.domain.authorization.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;

public record GrantRejected(
  GrantId grantId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {
  public static GrantRejected of(GrantId grantId, UserNo createdBy) {
    return new GrantRejected(grantId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
