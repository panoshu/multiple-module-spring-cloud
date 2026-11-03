package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.core.types.BatchId;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;

public record CopyFileCommand(
  FileId srcFileId,
  FileUsage targetUsage,
  BatchId businessBatchId,
  UserNo operatedBy
) {
}
