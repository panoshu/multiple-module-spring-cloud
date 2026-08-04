package com.example.core.domain.business.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * ApplicationSpawnedEvent
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 13:40
 */
public record ApplicationSpawnedEvent(
  EventId eventId,
  ApplicationId applicationId,
  LocalDateTime occurredOn
) implements DomainEvent {
  public static ApplicationSpawnedEvent of(ApplicationId applicationId) {
    return new ApplicationSpawnedEvent(EventId.generate(), applicationId, LocalDateTime.now());
  }
}
