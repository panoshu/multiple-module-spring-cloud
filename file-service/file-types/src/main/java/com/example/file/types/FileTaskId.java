package com.example.file.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

@IdDefinition(type = IdType.ULID)
public record FileTaskId(String value) implements Identifier<String> {


  public FileTaskId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileTaskId empty");
    }
  }

  public static FileTaskId of(String value) {
    return new FileTaskId(value);
  }

}

