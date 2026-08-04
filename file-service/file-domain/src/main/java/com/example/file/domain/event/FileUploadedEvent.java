package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.FileId;

import java.time.LocalDateTime;

public record FileUploadedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileId fileId,
  String originalName,
  long size,
  String contentType,
  String md5,
  FileUsage usage
) implements DomainEvent {

  public static FileUploadedEvent of(FileMetadata file) {
    return new FileUploadedEvent(
      EventId.generate(),
      LocalDateTime.now(),
      file.id(),
      file.originalName(),
      file.size(),
      file.contentType(),
      file.md5(),
      file.usage()
    );
  }
}
