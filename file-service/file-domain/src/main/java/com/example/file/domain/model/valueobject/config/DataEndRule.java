package com.example.file.domain.model.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record DataEndRule(
    List<String> markers,
    int blankRowCount
) implements ValueObject {
  public DataEndRule {
    markers = markers == null ? List.of() : List.copyOf(markers);
    if (blankRowCount < 0) blankRowCount = 0;
  }
}
