package com.example.core.domain.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;

import java.time.LocalDateTime;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 12:46
 */
public record FormUploadedEvent(
  EventId eventId,
  FormId formId,
  FileId fileId,
  String fileName,
  LocalDateTime occurredOn
) implements DomainEvent {

  public static FormUploadedEvent of(FormId formId, FileId fileId, String fileName) {
    return new FormUploadedEvent(EventId.generate(), formId, fileId, fileName, LocalDateTime.now());
  }
}
