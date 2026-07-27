package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.UserEnabledEventDTO;
import com.example.iam.domain.authentication.event.UserEnabledEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 用户已启用领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class UserEnabledEventConverter
    implements IntegrationEventConverter<UserEnabledEvent> {

  @Override
  public Class<UserEnabledEvent> supportedEventType() {
    return UserEnabledEvent.class;
  }

  @Override
  public Object toIntegrationEvent(UserEnabledEvent event) {
    return new UserEnabledEventDTO(
        event.eventId().value(),
        event.userId().value(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.USER_ENABLED;
  }
}
