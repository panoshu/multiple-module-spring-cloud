package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询批次详情
 *
 * @author panoshu
 */
public record GetBatchDetailQuery(
  @NotBlank(message = "批次ID不能为空") String batchId
) {
}
