package com.example.auth.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.domain.channel.valueobject.PermissionSnapshot;
import com.example.auth.types.SecondaryAuthSessionId;

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
