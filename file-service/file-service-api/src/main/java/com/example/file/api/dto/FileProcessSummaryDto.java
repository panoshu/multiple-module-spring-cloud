package com.example.file.api.dto;

import java.util.List;

/**
 * API 层专属的文件处理摘要
 */
public record FileProcessSummaryDto(
  String fileName,
  boolean success,
  List<String> outputOssUrls,
  String errorExcelUrl
) {
}
