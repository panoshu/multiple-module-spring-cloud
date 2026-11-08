package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record SaveTemplateConfigRequest(
  @NotBlank String bizType,
  String templateCode,
  String version,
  String errorPolicy,
  Map<String, Object> canonicalModel,
  List<Map<String, Object>> validationRules,
  List<Map<String, Object>> derivationRules,
  Map<String, Object> splitConfig,
  List<Map<String, Object>> sourceTemplates,
  Map<String, Object> targetMapping,
  @NotBlank String operator
) {
}
