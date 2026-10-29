package com.pension.permission.domain.permission.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.MenuCode;
import com.pension.permission.types.PermissionMenuId;

import java.time.LocalDateTime;

public record PermissionMenuMoved(
  PermissionMenuId menuId,
  MenuCode menuCode,
  PermissionMenuId newParentId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo movedBy
) implements DomainEvent {

  public static PermissionMenuMoved of(PermissionMenuId menuId, MenuCode menuCode, PermissionMenuId newParentId, UserNo movedBy) {
    return new PermissionMenuMoved(menuId, menuCode, newParentId, EventId.generate(), LocalDateTime.now(), movedBy);
  }
}
