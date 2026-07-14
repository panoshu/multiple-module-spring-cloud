package com.example.share.logging.sanitization.strategy.param;

import jakarta.validation.constraints.Min;

/**
 * 保留首尾策略参数
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:33
 */
public record KeepFirstLastParams(
  @Min(value = 0, message = "show-prefix must be >= 0")
  int showPrefix,

  @Min(value = 0, message = "show-suffix must be >= 0")
  int showSuffix
) implements StrategyParams {

}
