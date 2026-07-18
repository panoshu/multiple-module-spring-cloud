package com.example.file.types;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.IdType;
import com.example.shared.primitives.identity.Identifier;

/**
 * 文件解析任务 ID（ULID，分布式友好）
 */
@IdDefinition(type = IdType.ULID)
public record FileTaskId(String value) implements Identifier<String> {
  public FileTaskId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileTaskId empty");
    }
  }
  public static FileTaskId of(String value) { return new FileTaskId(value); }
}
