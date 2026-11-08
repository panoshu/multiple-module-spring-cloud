package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次创建响应
 *
 * @author panoshu
 */
public record BatchCreatedResponse(
  String batchId,
  String status,
  LocalDateTime createTime
) {
}
