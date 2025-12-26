package com.example.share.logging.obfuscate.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RuleBuilder
 * 修正：实现智能别名分发规则
 */
public class RuleBuilder {

  /**
   * 构建 JSON Body 规则
   * 规则：排除 header. 和 query. 开头的，其余均视为 JsonPath
   */
  public Map<String, ValidatedFieldConfig> buildJsonPathRules(
    Map<String, ValidatedFieldConfig> fieldConfigs,
    boolean enableWildcardPaths) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      // 【修改点1】只要不是 header. 或 query. 开头，都归类为 Body 规则
      .filter(alias -> !alias.startsWith("header.") && !alias.startsWith("query."))
      .forEach(alias -> {
        // 【修改点2】智能标准化：如果没有以 $ 开头，默认加上 $.
        String normalizedPath = alias.startsWith("$") ? alias : "$." + alias;

        rules.put(normalizedPath, fieldConfig);

        // 自动注册递归路径 (保持原有逻辑)
        if (enableWildcardPaths) {
          autoRegisterRecursivePaths(normalizedPath, fieldConfig, rules);
        }
      }));

    return Map.copyOf(rules);
  }

  /**
   * 构建 Header 规则
   * 规则：只提取 header. 开头的
   */
  public Map<String, ValidatedFieldConfig> buildHeaderRules(
    Map<String, ValidatedFieldConfig> fieldConfigs) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      .filter(alias -> alias.startsWith("header."))
      .map(alias -> alias.substring("header.".length())) // 去掉前缀
      .forEach(headerName -> {
        // HTTP Header 大小写不敏感，统一转小写存储
        rules.put(headerName.toLowerCase(), fieldConfig);
      }));

    return Map.copyOf(rules);
  }

  /**
   * 构建 Query Param 规则
   * 规则：只提取 query. 开头的
   */
  public Map<String, ValidatedFieldConfig> buildQueryRules(
    Map<String, ValidatedFieldConfig> fieldConfigs) {

    Map<String, ValidatedFieldConfig> rules = new ConcurrentHashMap<>();

    fieldConfigs.forEach((fieldName, fieldConfig) -> fieldConfig.aliases().stream()
      .filter(alias -> alias.startsWith("query."))
      .map(alias -> alias.substring("query.".length())) // 去掉前缀
      .forEach(paramName -> {
        // URL 参数通常大小写敏感，但为了容错往往也转小写匹配（取决于你的业务需求）
        // 这里保持与 Filter 逻辑一致，使用小写
        rules.put(paramName.toLowerCase(), fieldConfig);
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
