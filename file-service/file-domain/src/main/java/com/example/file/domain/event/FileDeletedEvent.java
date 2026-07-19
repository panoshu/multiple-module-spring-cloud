package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

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
