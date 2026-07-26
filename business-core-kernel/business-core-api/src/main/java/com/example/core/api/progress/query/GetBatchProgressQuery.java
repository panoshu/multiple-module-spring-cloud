package com.example.core.api.progress.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询批次进度
 *
 * @author panoshu
 */
public record GetBatchProgressQuery(
    @NotBlank(message = "批次ID不能为空") String batchId
) {
}
