package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record StoreFileCommand(
    String originalName,
    long size,
    String contentType,
    FileUsage usage,
    String bizType,
    String sourceApp,
    BatchId businessBatchId,
    UserNo uploadedBy,
    LocalDateTime expiresAt
) {}
