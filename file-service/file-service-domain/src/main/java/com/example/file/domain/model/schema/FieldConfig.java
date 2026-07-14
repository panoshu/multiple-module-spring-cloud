package com.example.file.domain.model.schema;

import com.example.file.domain.model.enums.DataType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.locator.Locator;
import com.example.file.domain.model.rule.ValidationRule;

import java.util.List;
import java.util.Objects;

// 2. 扩展 FieldConfig 增加 rules 属性
public record FieldConfig(
  String jsonKey, FieldType fieldType, String label, DataType dataType,
  String format, boolean required, List<ValidationRule> rules,
  Locator locator, Style style
) {
  public FieldConfig {
    if (fieldType == null) {
      fieldType = (jsonKey != null && jsonKey.endsWith("_text")) ? FieldType.TEXT_FIELD : FieldType.DATA_FIELD;
    }
    if (dataType == null && fieldType == FieldType.DATA_FIELD) {
      dataType = DataType.STRING;
    }
    rules = Objects.requireNonNullElse(rules, List.of());
    style = Objects.requireNonNullElse(style, Style.DEFAULT);
  }
}
