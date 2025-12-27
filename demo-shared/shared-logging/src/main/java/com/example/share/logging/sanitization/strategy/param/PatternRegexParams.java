package com.example.share.logging.sanitization.strategy.param;

import jakarta.validation.constraints.NotBlank;

/**
 * PatternRegexParams
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:34
 */
public record PatternRegexParams(
  @NotBlank(message = "pattern must not be blank")
  String pattern,

  String replacement
) implements StrategyParams {

  public PatternRegexParams {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("pattern must not be blank");
    }
    try {
      java.util.regex.Pattern.compile(pattern);
    } catch (java.util.regex.PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
    }
  }
}
