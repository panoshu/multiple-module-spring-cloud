package com.example.file.api.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TemplateConfigDTO(
  String configId,
  String bizType,
  String templateCode,
  String version,
  String status,
  String errorPolicy,
  Map<String, Object> canonicalModel,
  List<Map<String, Object>> validationRules,
  List<Map<String, Object>> derivationRules,
  Map<String, Object> splitConfig,
  List<Map<String, Object>> sourceTemplates,
  Map<String, Object> targetMapping,
  LocalDateTime effectiveFrom,
  LocalDateTime effectiveTo
) {
}
