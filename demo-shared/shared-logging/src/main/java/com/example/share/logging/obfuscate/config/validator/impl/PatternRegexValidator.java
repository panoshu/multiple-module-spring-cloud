package com.example.share.logging.obfuscate.config.validator.impl;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.PatternRegexParams;
import com.example.share.logging.obfuscate.config.validator.StrategyValidator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PatternRegexValidator
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 16:50
 */
@Component
public class PatternRegexValidator implements StrategyValidator {

  @Override
  public ObfuscationStrategyType getStrategyType() {
    return ObfuscationStrategyType.PATTERN_REGEX;
  }

  @Override
  public PatternRegexParams validateAndConvert(Map<String, Object> params) {
    if (params == null) {
      throw new IllegalArgumentException("params must not be null for PATTERN_REGEX strategy");
    }

    String pattern = getStringParam(params, "pattern", null);
    String replacement = getStringParam(params, "replacement", "***");

    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("pattern is required for PATTERN_REGEX strategy");
    }

    return new PatternRegexParams(pattern, replacement);
  }

  private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
    if (params == null || !params.containsKey(key)) {
      return defaultValue;
    }
    Object value = params.get(key);
    return value != null ? value.toString() : defaultValue;
  }
}
