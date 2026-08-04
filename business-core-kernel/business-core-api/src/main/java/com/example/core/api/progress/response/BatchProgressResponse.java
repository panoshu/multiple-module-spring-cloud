package com.example.core.api.progress.response;

/**
 * 批次进度响应
 *
 * @author panoshu
 */
public record BatchProgressResponse(
  String batchId,
  String status,
  int totalApplicationCount,
  int successCount,
  int failedCount,
  int pendingCount
) {
}
