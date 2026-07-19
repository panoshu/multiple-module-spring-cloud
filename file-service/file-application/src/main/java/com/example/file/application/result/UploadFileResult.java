package com.example.file.application.result;

import java.time.LocalDateTime;

public record UploadFileResult(
    String fileTaskId,
    String status,
    LocalDateTime createdAt
) {}
