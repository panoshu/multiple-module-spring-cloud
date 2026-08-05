package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权完成事件.
 */
public record SecondaryAuthCompleted(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EffectiveIdentity effectiveIdentity,
  PermissionSnapshot permissionSnapshot,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthCompleted of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    EffectiveIdentity effectiveIdentity,
    PermissionSnapshot permissionSnapshot,
    UserNo createdBy
  ) {
    return new SecondaryAuthCompleted(
      sessionId, tellerAccountId, approverAccountId,
      effectiveIdentity, permissionSnapshot,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
