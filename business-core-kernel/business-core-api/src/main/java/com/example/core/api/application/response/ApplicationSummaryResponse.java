package com.example.core.api.application.response;

import java.time.LocalDateTime;

/**
 * 申请单摘要响应
 *
 * @author panoshu
 */
public record ApplicationSummaryResponse(
  String applicationId,
  String batchId,
  String status,
  String currentStep,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
