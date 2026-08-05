package com.pension.permission.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

/**
 * 二次授权会话 ID.
 */
@IdDefinition(type = IdType.ULID)
public record SecondaryAuthSessionId(String value) implements Identifier<String> {
  public SecondaryAuthSessionId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("SecondaryAuthSessionId cannot be blank.");
    }
  }
}
