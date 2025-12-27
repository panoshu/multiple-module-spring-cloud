package com.example.share.logging.sanitization.strategy.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.KeepFirstLastParams;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import com.example.share.logging.sanitization.strategy.SanitizationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:23
 */
@Slf4j
public class KeepFirstLastStrategy implements SanitizationStrategy {

  private static final int DEFAULT_KEEP = 1;

  @Override
  public SanitizationStrategyType getType() {
    return SanitizationStrategyType.KEEP_FIRST_LAST;
  }

  @Override
  public String sanitize(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case KeepFirstLastParams p -> keepFirstLastSanitize(value, p);
      case null -> throw new IllegalArgumentException(
        "KeepFirstLastStrategy requires KeepFirstLastParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("KeepFirstLastStrategy requires KeepFirstLastParams, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String keepFirstLastSanitize(String value, KeepFirstLastParams params) {
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
