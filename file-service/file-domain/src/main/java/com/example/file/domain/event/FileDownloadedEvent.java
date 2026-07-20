package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record FileDownloadedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileId fileId,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo downloader,
    String tokenHash
) implements DomainEvent {
    public static FileDownloadedEvent of(FileMetadata file, String tokenHash) {
        return new FileDownloadedEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
            file.uploadedBy(), tokenHash
        );
    }
}
