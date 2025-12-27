package com.example.share.logging.sanitization.context;

import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import com.example.share.logging.sanitization.properties.SanitizationProperties;
import com.example.share.logging.sanitization.properties.SanitizationStrategyType;

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
