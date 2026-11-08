package com.example.file.domain.event;

import com.example.file.domain.model.storage.aggregate.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.file.types.FileId;
import com.example.core.types.UserNo;

import java.time.LocalDateTime;

public record FileDeletedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileId fileId,
  String originalName,
  String deletedBy
) implements DomainEvent {

  public static FileDeletedEvent of(FileMetadata file, UserNo deletedBy) {
    return new FileDeletedEvent(
      EventId.generate(),
      LocalDateTime.now(),
      file.id(),
      file.originalName(),
      deletedBy != null ? deletedBy.value() : null
    );
  }
}
