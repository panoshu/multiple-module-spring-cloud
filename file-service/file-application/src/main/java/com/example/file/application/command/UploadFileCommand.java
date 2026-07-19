package com.example.file.application.command;

public record UploadFileCommand(
    String bizType,
    String templateCode,
    String sourceFileName,
    String sourceFileRef,
    String uploader,
    String clientRequestNo
) {}
