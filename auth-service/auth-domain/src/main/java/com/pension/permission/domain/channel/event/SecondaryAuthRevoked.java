package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权撤销事件.
 */
public record SecondaryAuthRevoked(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  String reason,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthRevoked of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    String reason,
    UserNo createdBy
  ) {
    return new SecondaryAuthRevoked(
      sessionId, tellerAccountId, approverAccountId, reason,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
