package com.example.file.application.command;

import com.example.file.domain.model.storage.valueobject.FileUsage;
import com.example.core.types.BatchId;
import com.example.core.types.UserNo;

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
) {
}
