package com.example.share.logging.obfuscate.strategy.impl;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.FullParams;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:22
 */
@Slf4j
@Component
public class FullObfuscationStrategy implements ObfuscationStrategy {
  private static final int MAX_OBFUSCATION_LENGTH = 100;
  private static final String OBFUSCATION_CHAR = "*";

  @Override
  public ObfuscationStrategyType getType() {
    return ObfuscationStrategyType.FULL;
  }

  @Override
  public String obfuscate(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case FullParams ignored -> fullObfuscateValue(value);
      case null -> throw new IllegalArgumentException(
        "FullObfuscationStrategy requires FullParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("FullObfuscationStrategy requires FullParams or null, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String fullObfuscateValue(String value) {
    int length = Math.min(value.length(), MAX_OBFUSCATION_LENGTH);
    return OBFUSCATION_CHAR.repeat(length);
  }
}
