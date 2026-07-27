package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.SecondaryAuthRevokedEventDTO;
import com.example.iam.domain.authentication.event.SecondaryAuthRevokedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 二次授权撤销领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class SecondaryAuthRevokedEventConverter
    implements IntegrationEventConverter<SecondaryAuthRevokedEvent> {

  @Override
  public Class<SecondaryAuthRevokedEvent> supportedEventType() {
    return SecondaryAuthRevokedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(SecondaryAuthRevokedEvent event) {
    return new SecondaryAuthRevokedEventDTO(
        event.eventId().value(),
        event.sessionId().value(),
        event.tellerId(),
        event.reason(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.SECONDARY_AUTH_REVOKED;
  }
}
