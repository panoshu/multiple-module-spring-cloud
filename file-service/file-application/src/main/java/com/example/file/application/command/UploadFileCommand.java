package com.example.file.application.command;

import com.example.shared.primitives.identity.FileId;

public record UploadFileCommand(
    String bizType,
    String templateCode,
    String sourceFileName,
    FileId sourceFileId,
    String uploader,
    String clientRequestNo
) {}
