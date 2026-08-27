package com.example.bff.shared.business.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建批次请求
 *
 * @author bff
 */
public record BffCreateBatchRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "计划编号不能为空") String planNo,
  String operatorRemark
) {
}
