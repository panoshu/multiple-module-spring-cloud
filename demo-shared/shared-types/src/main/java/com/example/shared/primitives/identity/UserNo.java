package com.example.shared.primitives.identity;

/**
 * 领域事件ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record UserNo(String value) implements Identifier<String> {

  public UserNo {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("UserNo cannot be null or blank.");
    }
  }

  public static UserNo of(String value) {
    return new UserNo(value);
  }
}
