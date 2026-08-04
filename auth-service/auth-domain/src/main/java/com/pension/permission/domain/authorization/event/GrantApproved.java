package com.pension.permission.domain.authorization.event;


import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;

public record GrantApproved(
  GrantId grantId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static GrantApproved of(GrantId grantId, UserNo createdBy) {
    return new GrantApproved(grantId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
