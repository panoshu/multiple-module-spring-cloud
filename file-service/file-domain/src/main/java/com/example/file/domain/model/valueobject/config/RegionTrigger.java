package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.TriggerMatchType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record RegionTrigger(
  TriggerMatchType matchType,
  int minMatchCount
) implements ValueObject {
  public RegionTrigger {
    if (minMatchCount <= 0) minMatchCount = 1;
  }
}
