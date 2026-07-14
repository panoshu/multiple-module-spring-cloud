package com.example.file.application.dto;

import java.util.List;

/**
 * FileProcessSummary
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 21:58
 */
public record FileProcessSummary(
  String fileName,
  boolean success,
  List<String> outputOssUrls, // 成功时的 JSON 链接
  String errorExcelUrl        // 失败时的错误反馈 Excel 链接
) {
}
