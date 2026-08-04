package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SessionId;

import java.time.LocalDateTime;

/**
 * SessionPlanSelected
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/4 19:36
 */
public record SessionPlanSelected(
  SessionId sessionId,
  UserNo primaryAccountId,
  PlanNo planId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {


  public static SessionPlanSelected of(
    SessionId sessionId,
    UserNo primaryAccountId,
    PlanNo planId,
    UserNo createdBy
  ) {

    return new SessionPlanSelected(
      sessionId,
      primaryAccountId,
      planId,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
