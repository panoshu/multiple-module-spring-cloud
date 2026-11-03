package com.example.shared.event.contract;

import com.example.shared.domain.event.DomainEvent;

import java.util.List;

public interface EventStore {
  void save(DomainEvent event, Object integrationEvent, String integrationType);

  long initDispatchLog(String eventId, String channel);

  void markSuccess(long logId);

  void markFailure(long logId, Throwable ex);

  List<PendingEntry> findPendingLogs(int batchSize);

  record PendingEntry(
    long logId,
    Object integrationEvent,
    String channel,
    String integrationType,
    int retryCount
  ) {
  }
}
