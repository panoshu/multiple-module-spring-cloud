package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PermissionRuleDisabledEventDTO;
import com.example.iam.domain.authorization.event.PermissionRuleDisabledEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 权限规则已禁用领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PermissionRuleDisabledEventConverter
    implements IntegrationEventConverter<PermissionRuleDisabledEvent> {

  @Override
  public Class<PermissionRuleDisabledEvent> supportedEventType() {
    return PermissionRuleDisabledEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PermissionRuleDisabledEvent event) {
    return new PermissionRuleDisabledEventDTO(
        event.eventId().value(),
        event.ruleId().value(),
        event.ruleCode(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PERMISSION_RULE_DISABLED;
  }
}
