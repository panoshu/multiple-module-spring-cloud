package com.example.bff.shared.business.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 申请单详情请求
 *
 * @author bff
 */
public record BffApplicationDetailRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
