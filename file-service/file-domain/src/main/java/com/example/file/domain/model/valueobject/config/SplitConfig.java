package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.SplitMissPolicy;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record SplitConfig(
    List<String> keys,
    SplitMissPolicy onMiss,
    String defaultOnMissValue,
    String fileNamingTemplate,
    boolean promoteToContext
) implements ValueObject {
  public SplitConfig {
    keys = keys == null ? List.of() : List.copyOf(keys);
    onMiss = onMiss == null ? SplitMissPolicy.ERROR : onMiss;
  }
}
