package com.example.shared.identifier.id;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 应用 ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record ApplicationId(String value) implements Identifier<String> {

  public ApplicationId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ApplicationId cannot be null or blank.");
    }
  }

  public static ApplicationId of(String value) {
    return new ApplicationId(value);
  }
}
