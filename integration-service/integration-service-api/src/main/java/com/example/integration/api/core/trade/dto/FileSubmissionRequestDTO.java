package com.example.integration.api.core.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 出站文件提交请求
 */
public record FileSubmissionRequestDTO(
  @NotBlank
  @Size(max = 64)
  String fileId,

  @NotBlank
  @Size(max = 10)
  String tradeCode,

  Map<String, String> headParams
) {
}
