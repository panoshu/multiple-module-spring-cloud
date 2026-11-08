package com.example.auth.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.types.SessionId;

import java.time.LocalDateTime;

/**
 * SessionIdentityElevated
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:35
 */
public record SessionIdentityElevated(
  SessionId sessionId,
  UserNo primaryAccountId,
  EffectiveIdentity previousIdentity,
  EffectiveIdentity newIdentity,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {


  public static SessionIdentityElevated of(
    SessionId sessionId,
    UserNo primaryAccountId,
    EffectiveIdentity previousIdentity,
    EffectiveIdentity newIdentity,
    UserNo createdBy
  ) {

    return new SessionIdentityElevated(
      sessionId,
      primaryAccountId,
      previousIdentity,
      newIdentity,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
