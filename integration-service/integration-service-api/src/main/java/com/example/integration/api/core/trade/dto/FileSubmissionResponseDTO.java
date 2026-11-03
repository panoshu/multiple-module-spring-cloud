package com.example.integration.api.core.trade.dto;

/**
 * 出站文件提交响应
 */
public record FileSubmissionResponseDTO(
  String tradeCode,
  String status,
  String message,
  String externalSerialNo
) {
}
