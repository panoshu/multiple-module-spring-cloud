package com.example.file.types;

import com.example.shared.primitives.identity.Identifier;

/**
 * 业务类型编码（语义是业务编码，非 ULID）
 * 例如：import_declare、export_apply
 */
public record BizType(String value) implements Identifier<String> {
  public BizType {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("BizType empty");
    }
  }
  public static BizType of(String value) { return new BizType(value); }
}
