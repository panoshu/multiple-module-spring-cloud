package com.pension.permission.types;

import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

public record PermissionBindingId(String value) implements Identifier<String> {
  public PermissionBindingId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PermissionBindingId cannot be blank");
    }
  }
}
