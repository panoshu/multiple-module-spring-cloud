package com.example.share.logging.obfuscate.config;

import com.example.share.logging.obfuscate.config.param.StrategyParams;

import java.util.List;

/**
 * ValidatedFieldConfig
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 18:03
 */
public record ValidatedFieldConfig(
  ObfuscateProperties.FieldConfig fieldConfig,
  StrategyParams validatedParams
) {
  public ValidatedFieldConfig {
    if (fieldConfig == null) {
      throw new IllegalArgumentException("fieldConfig must not be null");
    }
    if (validatedParams == null) {
      throw new IllegalArgumentException("validatedParams must not be null");
    }
  }

  public List<String> aliases() {
    return fieldConfig.aliases();
  }

  public ObfuscationStrategyType strategy() {
    return fieldConfig.strategy();
  }

  public String replacement() {
    return fieldConfig.replacement();
  }
}
