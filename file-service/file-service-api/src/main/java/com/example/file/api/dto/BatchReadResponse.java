package com.example.file.api.dto;

import java.util.List;

/**
 * API 层专属的批量处理响应
 */
public record BatchReadResponse(
  String batchTaskId,
  List<FileProcessSummaryDto> fileSummaries
) {
}
