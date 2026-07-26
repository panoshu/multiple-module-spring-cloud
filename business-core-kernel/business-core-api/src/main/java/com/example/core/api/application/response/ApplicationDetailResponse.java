package com.example.core.api.application.response;

import java.time.LocalDateTime;

/**
 * 申请单详情响应
 *
 * <p>{@code jsonFileId}/{@code packageFileId} 字段当前仅返回 ID 字符串,
 * 完整的文件/材料明细需由前端另行调用文件/材料接口查询。
 *
 * @author panoshu
 */
public record ApplicationDetailResponse(
    String applicationId,
    String batchId,
    String status,
    String currentStep,
    String jsonFileId,
    String packageFileId,
    LocalDateTime applyTime,
    LocalDateTime completeTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
