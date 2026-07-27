package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.UserDisabledEventDTO;
import com.example.iam.domain.authentication.event.UserDisabledEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 用户已禁用领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class UserDisabledEventConverter
    implements IntegrationEventConverter<UserDisabledEvent> {

  @Override
  public Class<UserDisabledEvent> supportedEventType() {
    return UserDisabledEvent.class;
  }

  @Override
  public Object toIntegrationEvent(UserDisabledEvent event) {
    return new UserDisabledEventDTO(
        event.eventId().value(),
        event.userId().value(),
        event.reason(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.USER_DISABLED;
  }
}
