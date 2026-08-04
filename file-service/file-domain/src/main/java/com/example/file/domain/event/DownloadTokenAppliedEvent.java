package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.*;

import java.time.LocalDateTime;

public record DownloadTokenAppliedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileId fileId,
  CustomerNo customerNo,
  ProductNo productNo,
  UserNo downloader,
  String tokenHash,
  LocalDateTime expireAt
) implements DomainEvent {
  public static DownloadTokenAppliedEvent of(FileMetadata file, String tokenHash, LocalDateTime expireAt) {
    return new DownloadTokenAppliedEvent(
      EventId.generate(), LocalDateTime.now(),
      file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
      file.uploadedBy(), tokenHash, expireAt
    );
  }
}
