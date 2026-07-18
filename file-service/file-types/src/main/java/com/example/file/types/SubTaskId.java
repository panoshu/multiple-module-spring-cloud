package com.example.file.types;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.IdType;
import com.example.shared.primitives.identity.Identifier;

/**
 * 子任务 ID（ULID）
 */
@IdDefinition(type = IdType.ULID)
public record SubTaskId(String value) implements Identifier<String> {
  public SubTaskId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("SubTaskId empty");
    }
  }
  public static SubTaskId of(String value) { return new SubTaskId(value); }
}
