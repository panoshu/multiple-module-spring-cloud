package com.example.file.api.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件解析完成集成事件 DTO
 * 对应 file-domain 的 FileParsedEvent 领域事件
 */
public record FileParsedEventDTO(
    String eventId,
    String fileTaskId,
    String bizType,
    String status,
    int totalSubTasks,
    List<SubTaskSummaryDTO> subTasks,
    String failureReason,
    LocalDateTime occurredOn
) {
  public record SubTaskSummaryDTO(
      String subTaskId,
      String splitKey,
      int totalRows,
      int validRows,
      int invalidRows,
      String status
  ) {}
}
