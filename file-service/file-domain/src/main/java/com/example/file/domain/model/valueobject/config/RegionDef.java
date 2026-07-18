package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.RegionType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record RegionDef(
    String name,
    RegionType type,
    String bindTo,
    RegionTrigger trigger,
    RegionStrategy strategy
) implements ValueObject {
  public RegionDef {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("RegionDef.name empty");
    if (type == null) throw new IllegalArgumentException("RegionDef.type null");
    if (strategy == null) throw new IllegalArgumentException("RegionDef.strategy null");
  }
}
