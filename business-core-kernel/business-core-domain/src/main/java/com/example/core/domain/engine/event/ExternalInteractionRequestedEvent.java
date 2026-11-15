package com.example.core.domain.engine.event;

import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * 外部协作已登记待派发事件（spec §13.1）。
 * <p>
 * 由 {@code ExternalInteraction.create} 注册，Repository 经 EventBus 发布（§13.2）。
 * AFTER_COMMIT 监听器据此尽快认领待派发协作，{@code @Scheduled} 恢复任务兜底；
 * 两者只推进协作状态，不重复触发外部调用。
 */
public record ExternalInteractionRequestedEvent(
  EventId eventId,
  ExternalInteractionId interactionId,
  ApplicationId applicationId,
  String requestNo,
  LocalDateTime occurredOn
) implements DomainEvent {

  public static ExternalInteractionRequestedEvent of(
      ExternalInteractionId interactionId, ApplicationId applicationId, String requestNo) {
    return new ExternalInteractionRequestedEvent(
      EventId.generate(), interactionId, applicationId, requestNo, LocalDateTime.now());
  }
}
