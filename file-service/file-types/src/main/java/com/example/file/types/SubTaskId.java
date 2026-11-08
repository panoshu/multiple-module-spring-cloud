package com.example.file.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

@IdDefinition(type = IdType.ULID)
public record SubTaskId(String value) implements Identifier<String> {


  public SubTaskId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("SubTaskId empty");
    }
  }

  public static SubTaskId of(String value) {
    return new SubTaskId(value);
  }

}

