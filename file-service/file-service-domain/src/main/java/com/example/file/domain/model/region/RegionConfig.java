package com.example.file.domain.model.region;

import com.example.file.domain.model.schema.FieldConfig;

import java.util.List;

public sealed interface RegionConfig permits DiscreteRegionConfig, HorizontalTableRegionConfig {
  String regionId();

  List<FieldConfig> fields();

  String type();
}
