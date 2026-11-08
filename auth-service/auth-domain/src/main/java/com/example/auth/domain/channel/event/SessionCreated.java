package com.example.auth.domain.channel.event;

import com.example.core.types.Channel;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.SessionId;

import java.time.LocalDateTime;

/**
 * SessionCreated
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:34
 */
public record SessionCreated(
  SessionId sessionId,
  UserNo primaryAccountId,
  Channel channel,
  LocalDateTime expiresAt,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {


  public static SessionCreated of(
    SessionId sessionId,
    UserNo primaryAccountId,
    Channel channel,
    LocalDateTime expiresAt,
    UserNo createdBy
  ) {

    return new SessionCreated(
      sessionId,
      primaryAccountId,
      channel,
      expiresAt,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
