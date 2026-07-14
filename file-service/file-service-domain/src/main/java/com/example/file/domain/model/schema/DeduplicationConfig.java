package com.example.file.domain.model.schema;

import java.util.List;
import java.util.Objects;

/**
 * DeduplicationConfig
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 22:30
 */
public record DeduplicationConfig(boolean enabled, List<String> uniqueKeys, String ttl) {
  public DeduplicationConfig {
    uniqueKeys = Objects.requireNonNullElse(uniqueKeys, List.of());
  }
}
