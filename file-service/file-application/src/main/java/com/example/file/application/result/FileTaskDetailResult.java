package com.example.file.application.result;

import java.time.LocalDateTime;
import java.util.List;

public record FileTaskDetailResult(
    String fileTaskId,
    String bizType,
    String templateCode,
    String sourceFileName,
    String status,
    int subTaskCount,
    int totalRows,
    int validCount,
    int invalidCount,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime finishedAt,
    List<SubTaskSummaryItem> subTasks
) {
  public record SubTaskSummaryItem(
      String subTaskId,
      String splitKey,
      int totalRows,
      int validRows,
      int invalidRows,
      String status
  ) {}
}
