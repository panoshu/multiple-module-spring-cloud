package com.example.share.logging.sanitization.context.support;

import com.example.share.logging.sanitization.context.SanitizationRule;
import com.example.share.logging.sanitization.properties.SanitizationProperties.FieldConfig;
import com.example.share.logging.sanitization.properties.SanitizationProperties.StrategyConfig;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidatorFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * ConfigurationValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 19:07
 */
public class ConfigurationValidator {

  private final StrategyValidatorFactory validatorFactory;

  public ConfigurationValidator(StrategyValidatorFactory validatorFactory) {
    this.validatorFactory = validatorFactory;
  }

  public Map<String, SanitizationRule> validateAndCreateConfigs(
    Map<String, FieldConfig> fields,
    Map<String, StrategyConfig> strategies) {

    Map<String, SanitizationRule> configs = new ConcurrentHashMap<>();

    for (Map.Entry<String, FieldConfig> entry : fields.entrySet()) {
      String fieldName = entry.getKey();
      FieldConfig fieldConfig = entry.getValue();

      try {
        SanitizationRule validatedConfig = createValidatedFieldConfig(fieldName, fieldConfig, strategies);
        configs.put(fieldName, validatedConfig);
      } catch (Exception e) {
        throw new IllegalArgumentException(
          String.format("Invalid configuration for field '%s': %s",
            fieldName, e.getMessage()), e);
      }
    }

    return Map.copyOf(configs);
  }

  private SanitizationRule createValidatedFieldConfig(
    String fieldName,
    FieldConfig fieldConfig,
    Map<String, StrategyConfig> strategies) {

    List<String> normalizedAliases = normalizeAliases(fieldName, fieldConfig.aliases());
    Map<String, Object> mergedParams = mergeParams(fieldConfig, strategies);
    FieldConfig enhancedFieldConfig = new FieldConfig(
      normalizedAliases,
      fieldConfig.strategy(),
      fieldConfig.replacement(),
      fieldConfig.params()
    );

    StrategyParams validatedParams = validatorFactory.validateParams(
      enhancedFieldConfig.strategy(),
      mergedParams
    );

    return new SanitizationRule(enhancedFieldConfig, validatedParams);
  }

  /**
   * 别名标准化与扩展逻辑
   * 1. 将配置 Key (fieldName) 自动合并到 aliases 列表中
   * 2. 识别无前缀别名，并将其扩展为 Body/Header/Query 三种规则
   */
  private List<String> normalizeAliases(String fieldName, List<String> originalAliases) {
    // 使用JDK 21的SequencedSet保持插入顺序并去重
    SequencedSet<String> result = new LinkedHashSet<>();

    // 1. 构造初始列表：配置的别名 + 字段名本身
    // 使用JDK 21的Stream API和集合工厂方法简化
    Stream.concat(
        Stream.ofNullable(originalAliases).flatMap(List::stream),
        Stream.of(fieldName)
      ).distinct()
      .forEachOrdered(source -> {
        if (source.startsWith("$") || source.startsWith("header.") || source.startsWith("query.")) {
          result.add(source);
        } else {
          // 【核心逻辑】无前缀 -> 视为"全范围生效"，展开为三个明确的规则
          // 使用JDK 21的集合工厂方法简化
          result.addAll(List.of(
            "$." + source,
            "header." + source,
            "query." + source
          ));
        }
      });

    return List.copyOf(result); // 返回不可变列表，符合JDK 21最佳实践
  }

  private Map<String, Object> mergeParams(FieldConfig fieldConfig,
                                          Map<String, StrategyConfig> strategies) {
    Map<String, Object> mergedParams = new java.util.HashMap<>();

    // 添加策略默认参数
    StrategyConfig defaultStrategyConfig =
      strategies.get(fieldConfig.strategy().name());
    if (defaultStrategyConfig != null && defaultStrategyConfig.params() != null) {
      mergedParams.putAll(defaultStrategyConfig.params());
    }

    // 字段参数覆盖默认参数
    mergedParams.putAll(fieldConfig.params());

    return Map.copyOf(mergedParams);
  }
}
