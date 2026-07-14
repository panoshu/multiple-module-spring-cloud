package com.example.core.domain.event;

import com.example.core.domain.vauleobject.PipelineExecutionResult;
import com.example.core.domain.vauleobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * PipelineExecutedEvent
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 09:11
 */
public record PipelineExecutedEvent(
  EventId eventId,
  ApplicationId applicationId,
  ApplicationFlowStep step,
  String pipelinePhase,
  PipelineExecutionResult executionResult,
  LocalDateTime occurredOn
) implements DomainEvent {
  public static PipelineExecutedEvent of(ApplicationId applicationId, ApplicationFlowStep step, String pipelinePhase, PipelineExecutionResult executionResult) {
    return new PipelineExecutedEvent(EventId.generate(), applicationId, step, pipelinePhase, executionResult, LocalDateTime.now());
  }
}
