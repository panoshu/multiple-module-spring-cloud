package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.SplitKeyType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record SplitKeyDef(
    String targetField,
    String sourcePath,
    SplitKeyType type
) implements ValueObject {
  public SplitKeyDef {
    type = type == null ? SplitKeyType.FIELD_VALUE : type;
  }
}
