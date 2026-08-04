package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SessionId;

import java.time.LocalDateTime;

/**
 * SessionClosed
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:38
 */
public record SessionClosed(
  SessionId sessionId,
  UserNo primaryAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {


  public static SessionClosed of(
    SessionId sessionId,
    UserNo primaryAccountId,
    UserNo createdBy
  ) {

    return new SessionClosed(
      sessionId,
      primaryAccountId,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
