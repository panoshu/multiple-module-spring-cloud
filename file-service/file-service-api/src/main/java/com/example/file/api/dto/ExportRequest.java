package com.example.file.api.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导出请求体
 */
public record ExportRequest(
  String schemaId,
  Map<String, Object> discreteData,
  List<Map<String, Object>> tableData
) {
  public ExportRequest {
    discreteData = Objects.requireNonNullElse(discreteData, Map.of());
    tableData = Objects.requireNonNullElse(tableData, List.of());
  }
}
