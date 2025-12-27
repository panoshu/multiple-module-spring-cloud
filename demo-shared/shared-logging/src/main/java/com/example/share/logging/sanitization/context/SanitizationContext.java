package com.example.share.logging.sanitization.context;

import com.example.share.logging.sanitization.context.support.ConfigurationValidator;
import com.example.share.logging.sanitization.context.support.RuleBuilder;
import com.example.share.logging.sanitization.properties.SanitizationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * 核心上下文, 持有索引好的规则
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:52
 */
@EnableConfigurationProperties(SanitizationProperties.class)
public class SanitizationContext {

  // 核心数据
  private final SanitizationProperties properties;
  private final Map<String, SanitizationRule> validatedFieldConfigs;

  // 规则缓存
  private Map<String, SanitizationRule> jsonPathRules;
  private Map<String, SanitizationRule> headerRules;
  private Map<String, SanitizationRule> queryRules;

  // 依赖的服务
  private final RuleBuilder ruleBuilder;

  public SanitizationContext(SanitizationProperties properties,
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

  public Map<String, SanitizationRule> getJsonPathRules() {
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

  public Map<String, SanitizationRule> getHeaderRules() {
    if (headerRules == null) {
      synchronized (this) {
        if (headerRules == null) {
          headerRules = ruleBuilder.buildHeaderRules(validatedFieldConfigs);
        }
      }
    }
    return Map.copyOf(headerRules);
  }

  public Map<String, SanitizationRule> getQueryRules() {
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

  public SanitizationProperties.GlobalConfig getGlobalConfig() {
    return properties.global();
  }

  public Map<String, SanitizationRule> getValidatedFieldConfigs() {
    return Map.copyOf(validatedFieldConfigs);
  }

  public SanitizationRule getFieldConfig(String fieldName) {
    return validatedFieldConfigs.get(fieldName);
  }

  public SanitizationRule findFieldConfigByAlias(String alias) {
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
