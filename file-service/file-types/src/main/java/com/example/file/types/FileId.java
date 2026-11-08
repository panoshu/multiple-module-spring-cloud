package com.example.file.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 文件 ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record FileId(String value) implements Identifier<String> {

  public FileId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileId cannot be null or blank.");
    }
  }

  public static FileId of(String value) {
    return new FileId(value);
  }
}
