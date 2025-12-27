package com.example.share.logging.sanitization.strategy.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.PartialHideParams;
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
public class PartialHideStrategy implements SanitizationStrategy {

  @Override
  public SanitizationStrategyType getType() {
    return SanitizationStrategyType.PARTIAL_HIDE;
  }

  @Override
  public String sanitize(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case PartialHideParams p -> partialHideSanitize(value, p);
      case null -> throw new IllegalArgumentException(
        "PartialHideStrategy requires PartialHideParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("PartialHideStrategy requires PartialHideParams, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String partialHideSanitize(String value, PartialHideParams params) {
    final int len = value.length();
    final int showPrefix = params.showPrefix();
    final int showSuffix = params.showSuffix();

    // 当显示长度之和超过或等于原长度时，返回全星号
    if (showPrefix + showSuffix >= len) {
      return "*".repeat(len);
    }

    String prefix = value.substring(0, showPrefix);
    String suffix = value.substring(len - showSuffix);
    String middle = "*".repeat(len - showPrefix - showSuffix);

    return prefix + middle + suffix;
  }
}
