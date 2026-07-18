package com.example.file.api.response;

import java.time.LocalDateTime;

public record FileTaskDTO(
    String fileTaskId,
    String bizType,
    String templateCode,
    String fileName,
    String status,
    int subTaskCount,
    int totalRows,
    int validCount,
    int invalidCount,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {}
