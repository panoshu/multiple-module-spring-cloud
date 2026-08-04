package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.*;

import java.time.LocalDateTime;

public record UploadTokenAppliedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileId fileId,
  CustomerNo customerNo,
  ProductNo productNo,
  UserNo uploader,
  String tokenHash,
  LocalDateTime expireAt
) implements DomainEvent {
  public static UploadTokenAppliedEvent of(FileMetadata file, String tokenHash, LocalDateTime expireAt) {
    return new UploadTokenAppliedEvent(
      EventId.generate(), LocalDateTime.now(),
      file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
      file.uploadedBy(), tokenHash, expireAt
    );
  }
}
