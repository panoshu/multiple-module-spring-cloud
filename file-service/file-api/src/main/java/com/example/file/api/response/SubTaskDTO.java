package com.example.file.api.response;

import java.time.LocalDateTime;

public record SubTaskDTO(
    String subTaskId,
    String fileTaskId,
    String splitKey,
    String status,
    int totalRows,
    int validRows,
    int invalidRows,
    LocalDateTime createdAt
) {}
