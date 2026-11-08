package com.example.file.application.command;

import com.example.file.types.FileId;

public record UploadFileCommand(
  String bizType,
  String category,
  java.util.Map<String, String> routingContext,
  String templateCode,
  String sourceFileName,
  FileId sourceFileId,
  String uploader,
  String clientRequestNo
) {
}
