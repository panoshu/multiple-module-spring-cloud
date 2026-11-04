package com.example.auth.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

@IdDefinition(type = IdType.ULID)
public record GrantId(String value) implements Identifier<String> {
  public GrantId {
    Objects.requireNonNull(value, "value");
  }
}
