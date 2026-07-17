package com.example.core.domain.event;

import com.example.core.domain.aggregate.vauleobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 申请单步骤流转事件,当申请单进入某个步骤时发布
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 16:45
 */
public record StepEnteredEvent(
  EventId eventId,
  ApplicationId applicationId,
  ApplicationFlowStep previousStep,
  ApplicationFlowStep currentStep,
  LocalDateTime occurredOn
) implements DomainEvent {
  public static StepEnteredEvent of(ApplicationId applicationId, ApplicationFlowStep previousStep, ApplicationFlowStep currentStep) {
    return new StepEnteredEvent(EventId.generate(), applicationId, previousStep, currentStep, LocalDateTime.now());
  }
}
