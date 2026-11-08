package com.example.auth.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

@IdDefinition(type = IdType.ULID)
public record PermissionMenuId(String value) implements Identifier<String> {
  public PermissionMenuId {
    Objects.requireNonNull(value, "value");
  }
}
