package com.example.share.logging.obfuscate.strategy.impl;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.KeepFirstLastParams;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:23
 */
@Slf4j
public class KeepFirstLastStrategy implements ObfuscationStrategy {

  private static final int DEFAULT_KEEP = 1;

  @Override
  public ObfuscationStrategyType getType() {
    return ObfuscationStrategyType.KEEP_FIRST_LAST;
  }

  @Override
  public String obfuscate(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case KeepFirstLastParams p -> keepFirstLastObfuscate(value, p);
      case null -> throw new IllegalArgumentException(
        "KeepFirstLastStrategy requires KeepFirstLastParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("KeepFirstLastStrategy requires KeepFirstLastParams, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String keepFirstLastObfuscate(String value, KeepFirstLastParams params) {
    final int len = value.length();
    final int keepFirst = params.showPrefix();
    final int keepLast = params.showSuffix();

    // 长度不足，不脱敏
    if (len <= keepFirst + keepLast) {
      return value;
    }

    int start = Math.min(keepFirst, len);
    int end = Math.max(0, len - keepLast);

    String prefix = value.substring(0, start);
    String suffix = value.substring(end);
    String middle = "*".repeat(end - start);

    return prefix + middle + suffix;
  }
}
