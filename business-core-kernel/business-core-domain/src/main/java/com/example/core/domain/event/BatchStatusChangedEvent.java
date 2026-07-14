package com.example.core.domain.event;

import com.example.core.domain.vauleobject.enums.status.BatchStatus;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 批次状态变化事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 12:55
 */
public record BatchStatusChangedEvent(
  EventId eventId,
  BatchId batchId,
  BatchStatus oldStatus,
  BatchStatus newStatus,
  LocalDateTime occurredOn
) implements DomainEvent {

  public static BatchStatusChangedEvent of(BatchId batchId, BatchStatus oldStatus, BatchStatus newStatus) {
    return new BatchStatusChangedEvent(EventId.generate(), batchId, oldStatus, newStatus, LocalDateTime.now());
  }
}
