package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PermissionRuleEnabledEventDTO;
import com.example.iam.domain.authorization.event.PermissionRuleEnabledEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 权限规则已启用领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PermissionRuleEnabledEventConverter
    implements IntegrationEventConverter<PermissionRuleEnabledEvent> {

  @Override
  public Class<PermissionRuleEnabledEvent> supportedEventType() {
    return PermissionRuleEnabledEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PermissionRuleEnabledEvent event) {
    return new PermissionRuleEnabledEventDTO(
        event.eventId().value(),
        event.ruleId().value(),
        event.ruleCode(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PERMISSION_RULE_ENABLED;
  }
}
