package com.example.auth.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.SessionId;

import java.time.LocalDateTime;

/**
 * SessionExpired
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:37
 */
public record SessionExpired(
  SessionId sessionId,
  UserNo primaryAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {


  public static SessionExpired of(
    SessionId sessionId,
    UserNo primaryAccountId,
    UserNo createdBy
  ) {

    return new SessionExpired(
      sessionId,
      primaryAccountId,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
