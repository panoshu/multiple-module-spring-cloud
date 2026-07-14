package com.example.file.domain.model.schema;

import com.example.file.domain.model.enums.DynamicFieldPolicy;

import java.util.Objects;

public record TableMeta(
  int idRowIndex,
  int nameRowIndex,
  int dataStartRow,
  int dataStartCol,
  boolean allowDynamicFields,
  String dynamicFieldInternalIdPrefix,
  String endMarker,
  DynamicFieldPolicy dynamicFieldPolicy
) {
  public TableMeta {
    dynamicFieldPolicy = Objects.requireNonNullElse(dynamicFieldPolicy, DynamicFieldPolicy.STRICT);
    dynamicFieldInternalIdPrefix = Objects.requireNonNullElse(dynamicFieldInternalIdPrefix, "");
  }

  public TableMeta innerConvert() {
    return new TableMeta(
      this.idRowIndex() - 1,
      this.nameRowIndex() - 1,
      this.dataStartRow() - 1,
      this.dataStartCol() - 1,
      this.allowDynamicFields, this.dynamicFieldInternalIdPrefix, this.endMarker, this.dynamicFieldPolicy
    );
  }

}
