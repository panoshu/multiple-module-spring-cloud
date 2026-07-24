package com.example.shared.primitives.identity;

/**
 * 表单 ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record FormId(String value) implements Identifier<String> {

  public FormId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FormId cannot be null or blank.");
    }
  }

  public static FormId of(String value) {
    return new FormId(value);
  }
}
