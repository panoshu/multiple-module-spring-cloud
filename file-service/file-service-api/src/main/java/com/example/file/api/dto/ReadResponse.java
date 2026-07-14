package com.example.file.api.dto;

import java.util.List;

/**
 * API 层专属的单文件读取响应
 */
public record ReadResponse(
  boolean isSuccess,
  List<String> ossUrls,
  String errorFileUrl,
  List<String> globalErrors
) {
}
