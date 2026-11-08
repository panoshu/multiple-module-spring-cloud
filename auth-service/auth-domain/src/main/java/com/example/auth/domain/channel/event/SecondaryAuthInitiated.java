package com.example.auth.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权发起事件.
 */
public record SecondaryAuthInitiated(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthInitiated of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthInitiated(
      sessionId, tellerAccountId, approverAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
