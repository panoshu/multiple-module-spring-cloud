package com.example.bff.shared.business.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 批次详情请求
 *
 * @author bff
 */
public record BffBatchDetailRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "批次ID不能为空") String batchId
) {
}
