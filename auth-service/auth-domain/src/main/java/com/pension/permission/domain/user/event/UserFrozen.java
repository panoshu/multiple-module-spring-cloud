package com.pension.permission.domain.user.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import lombok.Builder;
import lombok.With;

import java.time.LocalDateTime;

/**
 * UserActivated
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 16:05
 */
@With
@Builder
public record UserFrozen(
  UserNo userNo,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static UserFrozen of(UserNo userNo, UserNo createdBy) {
    return new UserFrozen(userNo, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
