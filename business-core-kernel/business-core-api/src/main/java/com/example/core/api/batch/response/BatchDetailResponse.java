package com.example.core.api.batch.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次详情响应
 *
 * @author panoshu
 */
public record BatchDetailResponse(
    String batchId,
    String businessType,
    String planNo,
    String customerNo,
    String customerName,
    String status,
    int totalFormCount,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    List<FormSummary> forms
) {
    /**
     * 批次下表单摘要
     */
    public record FormSummary(
        String formId,
        String fileName,
        String status,
        int applicationCount,
        LocalDateTime uploadTime
    ) {
    }
}
