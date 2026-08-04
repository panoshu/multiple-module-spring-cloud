package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.FileId;

import java.time.LocalDateTime;

public record FileMetadataCreatedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileId fileId,
  FileUsage usage,
  String bizType,
  String sourceApp,
  BatchId businessBatchId
) implements DomainEvent {

  public static FileMetadataCreatedEvent of(FileMetadata file) {
    return new FileMetadataCreatedEvent(
      EventId.generate(),
      LocalDateTime.now(),
      file.id(),
      file.usage(),
      file.bizType(),
      file.sourceApp(),
      file.businessBatchId()
    );
  }
}
