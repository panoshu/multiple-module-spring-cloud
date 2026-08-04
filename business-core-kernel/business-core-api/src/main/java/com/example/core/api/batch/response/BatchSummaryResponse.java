package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次摘要响应
 *
 * @author panoshu
 */
public record BatchSummaryResponse(
  String batchId,
  String businessType,
  String planNo,
  String status,
  int totalFormCount,
  int totalApplicationCount,
  int successCount,
  int failedCount,
  LocalDateTime createTime
) {
}
