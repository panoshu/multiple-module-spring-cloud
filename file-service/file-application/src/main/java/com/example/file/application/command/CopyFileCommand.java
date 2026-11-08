package com.example.file.application.command;

import com.example.file.domain.model.storage.valueobject.FileUsage;
import com.example.core.types.BatchId;
import com.example.file.types.FileId;
import com.example.core.types.UserNo;

public record CopyFileCommand(
  FileId srcFileId,
  FileUsage targetUsage,
  BatchId businessBatchId,
  UserNo operatedBy
) {
}
