package com.example.auth.domain.permission.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionMenuId;

import java.time.LocalDateTime;

public record PermissionMenuCreated(
  PermissionMenuId menuId,
  MenuCode menuCode,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static PermissionMenuCreated of(PermissionMenuId menuId, MenuCode menuCode, UserNo createdBy) {
    return new PermissionMenuCreated(menuId, menuCode, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
