package com.example.auth.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权过期事件.
 */
public record SecondaryAuthExpired(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthExpired of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthExpired(
      sessionId, tellerAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
