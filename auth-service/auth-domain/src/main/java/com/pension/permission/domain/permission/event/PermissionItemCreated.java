package com.pension.permission.domain.permission.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.PermissionItemId;

import java.time.LocalDateTime;

public record PermissionItemCreated(
  PermissionItemId itemId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static PermissionItemCreated of(PermissionItemId itemId, UserNo createdBy) {
    return new PermissionItemCreated(itemId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
