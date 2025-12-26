package com.example.share.logging.obfuscate.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * ObfuscateConfig
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:52
 */
@EnableConfigurationProperties(ObfuscateProperties.class)
public class ObfuscateConfig {

  // 核心数据
  private final ObfuscateProperties properties;
  private final Map<String, ValidatedFieldConfig> validatedFieldConfigs;

  // 规则缓存
  private Map<String, ValidatedFieldConfig> jsonPathRules;
  private Map<String, ValidatedFieldConfig> headerRules;
  private Map<String, ValidatedFieldConfig> queryRules;

  // 依赖的服务
  private final RuleBuilder ruleBuilder;

  public ObfuscateConfig(ObfuscateProperties properties,
                         ConfigurationValidator configurationValidator,
                         RuleBuilder ruleBuilder) {
    this.properties = properties;
    this.ruleBuilder = ruleBuilder;

    // 验证配置
    this.validatedFieldConfigs = configurationValidator.validateAndCreateConfigs(
      properties.fields(),
      properties.strategies()
    );
  }

  // ================ 懒加载规则 ================

  public Map<String, ValidatedFieldConfig> getJsonPathRules() {
    if (jsonPathRules == null) {
      synchronized (this) {
        if (jsonPathRules == null) {
          jsonPathRules = ruleBuilder.buildJsonPathRules(
            validatedFieldConfigs,
            getGlobalConfig().enableWildcardPaths()
          );
        }
      }
    }
    return Map.copyOf(jsonPathRules);
  }

  public Map<String, ValidatedFieldConfig> getHeaderRules() {
    if (headerRules == null) {
      synchronized (this) {
        if (headerRules == null) {
          headerRules = ruleBuilder.buildHeaderRules(validatedFieldConfigs);
        }
      }
    }
    return Map.copyOf(headerRules);
  }

  public Map<String, ValidatedFieldConfig> getQueryRules() {
    if (queryRules == null) {
      synchronized (this) {
        if (queryRules == null) {
          queryRules = ruleBuilder.buildQueryRules(validatedFieldConfigs);
        }
      }
    }
    return Map.copyOf(queryRules);
  }

  // ================ 简单委托方法 ================

  public ObfuscateProperties.GlobalConfig getGlobalConfig() {
    return properties.global();
  }

  public Map<String, ValidatedFieldConfig> getValidatedFieldConfigs() {
    return Map.copyOf(validatedFieldConfigs);
  }

  public ValidatedFieldConfig getFieldConfig(String fieldName) {
    return validatedFieldConfigs.get(fieldName);
  }

  public ValidatedFieldConfig findFieldConfigByAlias(String alias) {
    return validatedFieldConfigs.values().stream()
      .filter(config -> config.aliases().contains(alias))
      .findFirst()
      .orElse(null);
  }

  public boolean hasField(String fieldName) {
    return validatedFieldConfigs.containsKey(fieldName);
  }

  public Set<String> getFieldNames() {
    return Set.copyOf(validatedFieldConfigs.keySet());
  }
}
