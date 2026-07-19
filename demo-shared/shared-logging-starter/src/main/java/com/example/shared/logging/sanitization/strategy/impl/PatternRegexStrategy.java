package com.example.shared.logging.sanitization.strategy.impl;

import com.example.shared.logging.sanitization.properties.SanitizationStrategyType;
import com.example.shared.logging.sanitization.strategy.SanitizationStrategy;
import com.example.shared.logging.sanitization.strategy.param.PatternRegexParams;
import com.example.shared.logging.sanitization.strategy.param.StrategyParams;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:26
 */
@Slf4j
public class PatternRegexStrategy implements SanitizationStrategy {

  private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

  @Override
  public SanitizationStrategyType getType() {
    return SanitizationStrategyType.PATTERN_REGEX;
  }

  @Override
  public String sanitize(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case PatternRegexParams p -> patternRegexSanitize(value, p);
      case null -> throw new IllegalArgumentException(
        "PatternRegexStrategy requires PatternRegexParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("PatternRegexStrategy requires PatternRegexParams, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String patternRegexSanitize(String value, PatternRegexParams params) {
    String pattern = params.pattern();
    String replacement = params.replacement();

    if (pattern == null || pattern.isBlank()) {
      log.warn("Pattern parameter is empty for PATTERN_REGEX strategy");
      return "*".repeat(value.length());
    }

    try {
      Matcher matcher = patternCache.computeIfAbsent(pattern, Pattern::compile).matcher(value);
      return matcher.replaceAll(replacement);
    } catch (Exception e) {
      log.error("Regex replacement failed for pattern: {}", pattern, e);
      return "*".repeat(value.length());
    }
  }
}
