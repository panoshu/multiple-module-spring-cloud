package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PlanDelegationActivatedEventDTO;
import com.example.iam.domain.authorization.event.PlanDelegationActivatedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 计划代办关系已激活领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PlanDelegationActivatedEventConverter
    implements IntegrationEventConverter<PlanDelegationActivatedEvent> {

  @Override
  public Class<PlanDelegationActivatedEvent> supportedEventType() {
    return PlanDelegationActivatedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PlanDelegationActivatedEvent event) {
    return new PlanDelegationActivatedEventDTO(
        event.eventId().value(),
        event.delegationId().value(),
        event.delegationCode(),
        event.delegateePlanNo(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PLAN_DELEGATION_ACTIVATED;
  }
}
