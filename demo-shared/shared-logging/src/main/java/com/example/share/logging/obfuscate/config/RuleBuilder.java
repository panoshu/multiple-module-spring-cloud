package com.example.share.logging.obfuscate.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RuleBuilder
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 19:07
 */
public class RuleBuilder {

  public Map<String, ValidatedFieldConfig> buildJsonPathRules(
    Map<String, ValidatedFieldConfig> fieldConfigs,
    boolean enableWildcardPaths) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      .filter(alias -> alias.startsWith("$."))
      .forEach(jsonPath -> {
        String normalizedPath = jsonPath.startsWith("$") ? jsonPath : "$." + jsonPath;
        rules.put(normalizedPath, fieldConfig);

        // 自动注册递归路径
        if (enableWildcardPaths) {
          autoRegisterRecursivePaths(normalizedPath, fieldConfig, rules);
        }
      }));

    return Map.copyOf(rules);
  }

  public Map<String, ValidatedFieldConfig> buildHeaderRules(
    Map<String, ValidatedFieldConfig> fieldConfigs) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      .filter(alias -> alias.startsWith("header."))
      .map(alias -> alias.substring("header.".length()))
      .forEach(headerName -> {
        String normalizedHeaderName = headerName.toLowerCase();
        rules.put(normalizedHeaderName, fieldConfig);
      }));

    return Map.copyOf(rules);
  }

  public Map<String, ValidatedFieldConfig> buildQueryRules(
    Map<String, ValidatedFieldConfig> fieldConfigs) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      .filter(alias -> alias.startsWith("query."))
      .map(alias -> alias.substring("query.".length()))
      .forEach(paramName -> {
        String normalizedParamName = paramName.toLowerCase();
        rules.put(normalizedParamName, fieldConfig);
      }));

    return Map.copyOf(rules);
  }

  private void autoRegisterRecursivePaths(String jsonPath, ValidatedFieldConfig config,
                                          Map<String, ValidatedFieldConfig> rules) {
    if (!jsonPath.contains("..") && !jsonPath.contains("[*]") && !jsonPath.contains("[?")) {
      String extractedFieldName = extractFieldName(jsonPath);
      String recursivePath = "$.." + extractedFieldName;

      if (!rules.containsKey(recursivePath)) {
        rules.put(recursivePath, config);
      }
    }
  }

  private String extractFieldName(String jsonPath) {
    String cleanPath = jsonPath.replace("[", ".").replace("]", "");
    String[] parts = cleanPath.split("\\.");
    return parts[parts.length - 1];
  }
}
