package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.KvValuePosition;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Map;

public record KvStrategy(
    KvValuePosition valuePosition,
    Map<String, List<String>> labelAliases,
    int maxBlankRows
) implements RegionStrategy, ValueObject {
  public KvStrategy {
    labelAliases = labelAliases == null ? Map.of() : Map.copyOf(labelAliases);
    if (maxBlankRows <= 0) maxBlankRows = 3;
  }
}
