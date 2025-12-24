package com.example.share.logging.obfuscate.config;

import com.example.share.logging.obfuscate.config.param.StrategyParams;
import com.example.share.logging.obfuscate.config.validator.StrategyValidatorFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigurationValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 19:07
 */
@Service
public class ConfigurationValidator {

  private final StrategyValidatorFactory validatorFactory;

  public ConfigurationValidator(StrategyValidatorFactory validatorFactory) {
    this.validatorFactory = validatorFactory;
  }

  public Map<String, ValidatedFieldConfig> validateAndCreateConfigs(
    Map<String, ObfuscateProperties.FieldConfig> fields,
    Map<String, ObfuscateProperties.StrategyConfig> strategies) {

    Map<String, ValidatedFieldConfig> configs = new ConcurrentHashMap<>();

    for (Map.Entry<String, ObfuscateProperties.FieldConfig> entry : fields.entrySet()) {
      String fieldName = entry.getKey();
      ObfuscateProperties.FieldConfig fieldConfig = entry.getValue();

      try {
        ValidatedFieldConfig validatedConfig = createValidatedFieldConfig(fieldConfig, strategies);
        configs.put(fieldName, validatedConfig);
      } catch (Exception e) {
        throw new IllegalArgumentException(
          String.format("Invalid configuration for field '%s': %s",
            fieldName, e.getMessage()), e);
      }
    }

    return Map.copyOf(configs);
  }

  private ValidatedFieldConfig createValidatedFieldConfig(
    ObfuscateProperties.FieldConfig fieldConfig,
    Map<String, ObfuscateProperties.StrategyConfig> strategies) {

    Map<String, Object> mergedParams = mergeParams(fieldConfig, strategies);
    StrategyParams validatedParams = validatorFactory.validateParams(
      fieldConfig.strategy(),
      mergedParams
    );

    return new ValidatedFieldConfig(fieldConfig, validatedParams);
  }

  private Map<String, Object> mergeParams(ObfuscateProperties.FieldConfig fieldConfig,
                                          Map<String, ObfuscateProperties.StrategyConfig> strategies) {
    Map<String, Object> mergedParams = new java.util.HashMap<>();

    // 添加策略默认参数
    ObfuscateProperties.StrategyConfig defaultStrategyConfig =
      strategies.get(fieldConfig.strategy().name());
    if (defaultStrategyConfig != null && defaultStrategyConfig.params() != null) {
      mergedParams.putAll(defaultStrategyConfig.params());
    }

    // 字段参数覆盖默认参数
    mergedParams.putAll(fieldConfig.params());

    return Map.copyOf(mergedParams);
  }
}
