package com.example.share.logging.obfuscate.strategy.impl;

import com.example.share.logging.obfuscate.config.ObfuscationStrategyType;
import com.example.share.logging.obfuscate.config.param.PartialHideParams;
import com.example.share.logging.obfuscate.config.param.StrategyParams;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:23
 */
@Slf4j
public class PartialHideStrategy implements ObfuscationStrategy {

  @Override
  public ObfuscationStrategyType getType() {
    return ObfuscationStrategyType.PARTIAL_HIDE;
  }

  @Override
  public String obfuscate(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    return switch (params) {
      case PartialHideParams p -> partialHideObfuscate(value, p);
      case null -> throw new IllegalArgumentException(
        "PartialHideStrategy requires PartialHideParams, but got null");
      default -> throw new IllegalArgumentException(
        String.format("PartialHideStrategy requires PartialHideParams, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private String partialHideObfuscate(String value, PartialHideParams params) {
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
