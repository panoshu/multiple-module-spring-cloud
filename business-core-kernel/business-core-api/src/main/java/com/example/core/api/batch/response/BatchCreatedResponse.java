package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次创建响应
 *
 * @author panoshu
 */
public record BatchCreatedResponse(
    Long batchId,
    String batchNo,
    String status,
    LocalDateTime createTime
) {
}
