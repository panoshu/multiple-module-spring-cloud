package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询未完成/处理中业务批次
 *
 * @author panoshu
 */
public record FindActiveBatchQuery(
  @NotBlank(message = "计划编号不能为空") String planNo,
  @NotBlank(message = "业务类型不能为空") String businessType
) {
}
