package com.example.shared.logging.sanitization.context;

import com.example.shared.logging.sanitization.properties.SanitizationProperties;
import com.example.shared.logging.sanitization.properties.SanitizationStrategyType;
import com.example.shared.logging.sanitization.strategy.param.StrategyParams;

import java.util.List;

/**
 * SanitizationRule
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 18:03
 */
public record SanitizationRule(
  SanitizationProperties.FieldConfig fieldConfig,
  StrategyParams validatedParams
) {
  public SanitizationRule {
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

  public SanitizationStrategyType strategy() {
    return fieldConfig.strategy();
  }

  public String replacement() {
    return fieldConfig.replacement();
  }
}
