package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

public record CopyFileCommand(
    FileId srcFileId,
    FileUsage targetUsage,
    BatchId businessBatchId,
    UserNo operatedBy
) {}
