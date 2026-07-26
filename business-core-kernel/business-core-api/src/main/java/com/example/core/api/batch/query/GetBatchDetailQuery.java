package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotNull;

/**
 * 查询批次详情
 *
 * @author panoshu
 */
public record GetBatchDetailQuery(
    @NotNull(message = "批次ID不能为空") Long batchId
) {
}
