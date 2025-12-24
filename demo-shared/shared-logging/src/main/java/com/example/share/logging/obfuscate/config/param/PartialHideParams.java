package com.example.share.logging.obfuscate.config.param;

import jakarta.validation.constraints.Min;

/**
 * 部分隐藏策略参数
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:32
 */
public record PartialHideParams(
  @Min(value = 0, message = "show-prefix must be >= 0")
  int showPrefix,

  @Min(value = 0, message = "show-suffix must be >= 0")
  int showSuffix
) implements StrategyParams {
}
