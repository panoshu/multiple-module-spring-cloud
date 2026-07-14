package com.example.file.application.dto;

import java.util.List;

/**
 * BatchReadResult
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 21:58
 */
public record BatchReadResult(
  String batchTaskId,
  List<FileProcessSummary> fileSummaries
) {
}
