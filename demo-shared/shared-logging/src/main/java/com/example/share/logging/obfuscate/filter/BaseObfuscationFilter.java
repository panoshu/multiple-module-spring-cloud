package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * BaseObfuscationFilter
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 19:00
 */
@Slf4j
public abstract class BaseObfuscationFilter {

  protected final ObfuscateConfig config;
  protected final ObfuscationStrategyFactory strategyFactory;

  protected BaseObfuscationFilter(ObfuscateConfig config,
                                  ObfuscationStrategyFactory strategyFactory) {
    this.config = config;
    this.strategyFactory = strategyFactory;
  }

  protected String obfuscateValue(String value, ValidatedFieldConfig fieldConfig) {
    if (value == null || value.isBlank() || fieldConfig == null) {
      return value;
    }

    try {
      return strategyFactory.obfuscate(
        fieldConfig.strategy(),
        value,
        fieldConfig.validatedParams()
      );
    } catch (Exception e) {
      log.warn("Failed to obfuscate value with strategy {}: {}",
        fieldConfig.strategy(), e.getMessage());
      return "*".repeat(Math.min(value.length(), 100));
    }
  }

  protected boolean isEnabled() {
    return config.getGlobalConfig().enable();
  }
}
