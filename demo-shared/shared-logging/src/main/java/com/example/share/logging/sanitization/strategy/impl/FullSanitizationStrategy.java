package com.example.share.logging.sanitization.strategy.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.FullParams;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import com.example.share.logging.sanitization.strategy.SanitizationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:22
 */
@Slf4j
public class FullSanitizationStrategy implements SanitizationStrategy {
  private static final int MAX_OBFUSCATION_LENGTH = 100;
  private static final String OBFUSCATION_CHAR = "*";

  @Override
  public SanitizationStrategyType getType() {
    return SanitizationStrategyType.FULL;
  }

  @Override
  public String sanitize(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case FullParams ignored -> fullSanitizeValue(value);
      case null -> throw new IllegalArgumentException(
        "FullObfuscationStrategy requires FullParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("FullObfuscationStrategy requires FullParams or null, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String fullSanitizeValue(String value) {
    int length = Math.min(value.length(), MAX_OBFUSCATION_LENGTH);
    return OBFUSCATION_CHAR.repeat(length);
  }
}
