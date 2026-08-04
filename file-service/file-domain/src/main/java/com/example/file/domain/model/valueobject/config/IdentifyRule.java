package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.IdentifyMode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

public record IdentifyRule(
  IdentifyMode mode, List<String> fingerprint
) implements ValueObject {
  public IdentifyRule {
    if (mode == null) mode = IdentifyMode.AUTO;
    fingerprint = fingerprint == null ? List.of() : List.copyOf(fingerprint);
  }
}
