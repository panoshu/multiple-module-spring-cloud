package com.example.file.domain.model.schema;

import java.util.List;
import java.util.Objects;

/**
 * SplitStrategyConfig
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 22:30
 */
public record SplitStrategyConfig(boolean enabled, List<String> splitBy, String outputNaming) {
  public SplitStrategyConfig {
    splitBy = Objects.requireNonNullElse(splitBy, List.of());
    outputNaming = Objects.requireNonNullElse(outputNaming, "${bizType}_${splitValue}.json");
  }
}
