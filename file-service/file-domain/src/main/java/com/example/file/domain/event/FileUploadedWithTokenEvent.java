package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;

public record FileUploadedWithTokenEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileId fileId,
    String originalName,
    long size,
    String digest,
    String tokenHash
) implements DomainEvent {
    public static FileUploadedWithTokenEvent of(FileMetadata file, String tokenHash) {
        return new FileUploadedWithTokenEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.originalName(), file.size(), file.digest(), tokenHash
        );
    }
}
