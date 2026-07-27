package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PermissionRuleCreatedEventDTO;
import com.example.iam.domain.authorization.event.PermissionRuleCreatedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 权限规则已创建领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PermissionRuleCreatedEventConverter
    implements IntegrationEventConverter<PermissionRuleCreatedEvent> {

  @Override
  public Class<PermissionRuleCreatedEvent> supportedEventType() {
    return PermissionRuleCreatedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PermissionRuleCreatedEvent event) {
    return new PermissionRuleCreatedEventDTO(
        event.eventId().value(),
        event.ruleId().value(),
        event.ruleCode(),
        event.subjectType().name(),
        event.subjectId(),
        event.businessCode().value(),
        event.overrideMode().name(),
        event.priority(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PERMISSION_RULE_CREATED;
  }
}
