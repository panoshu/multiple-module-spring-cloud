package com.example.file.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 模板配置 ID（ULID）
 */
@IdDefinition(type = IdType.ULID)
public record TemplateConfigId(String value) implements Identifier<String> {
  public TemplateConfigId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TemplateConfigId empty");
    }
  }

  public static TemplateConfigId of(String value) {
    return new TemplateConfigId(value);
  }
}
