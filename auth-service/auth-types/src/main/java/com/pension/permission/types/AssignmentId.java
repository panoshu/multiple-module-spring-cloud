package com.pension.permission.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

@IdDefinition(type = IdType.ULID)
public record AssignmentId(String value) implements Identifier<String> {
  public AssignmentId {
    Objects.requireNonNull(value, "value");
  }

}
