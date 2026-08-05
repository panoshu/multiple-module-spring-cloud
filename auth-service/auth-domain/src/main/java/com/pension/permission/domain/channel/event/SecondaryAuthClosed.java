package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权关闭事件.
 */
public record SecondaryAuthClosed(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthClosed of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthClosed(
      sessionId, tellerAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
