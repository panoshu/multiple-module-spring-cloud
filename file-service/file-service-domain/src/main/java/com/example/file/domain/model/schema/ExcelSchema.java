package com.example.file.domain.model.schema;

import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.RenderMode;
import com.example.file.domain.model.enums.SchemaType;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.region.RegionConfig;

import java.util.List;

public record ExcelSchema(
  String schemaId,
  BizType bizType,
  SchemaType type,
  RenderMode renderMode,
  String templateSource,
  String targetWriteSchemaId,
  DeduplicationConfig deduplication,
  SplitStrategyConfig splitStrategy,
  ErrorFeedbackConfig errorFeedback,
  List<RegionConfig> regions
) {
  public List<FieldConfig> getAllDataFields() {
    return regions.stream()
      .flatMap(region -> switch (region) {
        case DiscreteRegionConfig dr -> dr.fields().stream();
        case HorizontalTableRegionConfig htr -> htr.fields().stream();
      })
      .filter(f -> f.fieldType() == FieldType.DATA_FIELD)
      .toList();
  }
}
