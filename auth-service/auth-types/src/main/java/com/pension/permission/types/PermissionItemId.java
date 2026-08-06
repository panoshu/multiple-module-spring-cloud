package com.pension.permission.types;

import com.example.shared.identifier.contract.Identifier;

public record PermissionItemId(String value) implements Identifier<String> {
  public PermissionItemId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PermissionItemId value cannot be blank");
    }
  }
}
