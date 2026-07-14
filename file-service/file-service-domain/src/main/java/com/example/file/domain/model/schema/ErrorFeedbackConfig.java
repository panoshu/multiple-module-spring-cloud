package com.example.file.domain.model.schema;

import java.util.Objects;

/**
 * ErrorFeedbackConfig
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 22:31
 */
public record ErrorFeedbackConfig(boolean enabled, String appendColumnName, String outputSuffix) {
  public ErrorFeedbackConfig {
    appendColumnName = Objects.requireNonNullElse(appendColumnName, "校验错误信息");
    outputSuffix = Objects.requireNonNullElse(outputSuffix, "_errors.xlsx");
  }
}
