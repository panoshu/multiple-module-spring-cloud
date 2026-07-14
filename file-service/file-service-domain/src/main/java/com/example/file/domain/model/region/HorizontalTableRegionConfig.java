package com.example.file.domain.model.region;

import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.TableMeta;

import java.util.List;

public record HorizontalTableRegionConfig(
  String regionId,
  TableMeta tableMeta,
  List<FieldConfig> fields
) implements RegionConfig {
  @Override
  public String type() {
    return "HorizontalTable";
  }
}
