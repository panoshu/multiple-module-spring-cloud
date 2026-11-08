package com.example.bff.shared.business.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 批次概览请求（聚合查询）
 *
 * @author bff
 */
public record BffBatchOverviewRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "批次ID不能为空") String batchId
) {
}
