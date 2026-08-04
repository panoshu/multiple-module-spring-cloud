package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record UploadFileRequest(
  @NotBlank String bizType,
  String templateCode,
  @NotBlank String fileName,
  long fileSize,
  @NotBlank String uploader,
  String clientRequestNo
) {
}
