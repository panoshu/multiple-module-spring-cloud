package com.example.core.domain.business.event;

import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.EventId;

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
