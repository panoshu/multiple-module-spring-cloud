package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.PlanDelegationRevokedEventDTO;
import com.example.iam.domain.authorization.event.PlanDelegationRevokedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 计划代办关系已撤销领域事件 -> 集成事件转换器。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class PlanDelegationRevokedEventConverter
    implements IntegrationEventConverter<PlanDelegationRevokedEvent> {

  @Override
  public Class<PlanDelegationRevokedEvent> supportedEventType() {
    return PlanDelegationRevokedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(PlanDelegationRevokedEvent event) {
    return new PlanDelegationRevokedEventDTO(
        event.eventId().value(),
        event.delegationId().value(),
        event.delegationCode(),
        event.delegateePlanNo(),
        event.reason(),
        event.operator().value(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.PLAN_DELEGATION_REVOKED;
  }
}
