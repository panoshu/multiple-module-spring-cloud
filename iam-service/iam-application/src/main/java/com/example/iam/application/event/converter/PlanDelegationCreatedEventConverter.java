package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PlanDelegationCreatedEventDTO;
import com.example.iam.domain.authorization.event.PlanDelegationCreatedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 计划代办关系已创建领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PlanDelegationCreatedEventConverter
    implements IntegrationEventConverter<PlanDelegationCreatedEvent> {

  @Override
  public Class<PlanDelegationCreatedEvent> supportedEventType() {
    return PlanDelegationCreatedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PlanDelegationCreatedEvent event) {
    return new PlanDelegationCreatedEventDTO(
        event.eventId().value(),
        event.delegationId().value(),
        event.delegationCode(),
        event.delegatorPlanNo(),
        event.delegateePlanNo(),
        event.delegationType().name(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PLAN_DELEGATION_CREATED;
  }
}
