package com.example.auth.domain.authorization.event;


import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.GrantId;

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
