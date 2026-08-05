package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权拒绝事件.
 */
public record SecondaryAuthRejected(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthRejected of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthRejected(
      sessionId, tellerAccountId, approverAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
