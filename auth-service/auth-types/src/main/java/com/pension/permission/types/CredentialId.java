package com.pension.permission.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

@IdDefinition(type = IdType.ULID)
public record CredentialId(String value) implements Identifier<String> {
  public CredentialId {
    Objects.requireNonNull(value, "value");
  }
}
