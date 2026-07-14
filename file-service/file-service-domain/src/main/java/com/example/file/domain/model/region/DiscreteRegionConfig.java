package com.example.file.domain.model.region;

import com.example.file.domain.model.schema.FieldConfig;

import java.util.List;

public record DiscreteRegionConfig(
  String regionId,
  int rows, // 🟢 明确声明该离散区域向下占用几行高度
  List<FieldConfig> fields
) implements RegionConfig {
  @Override
  public String type() {
    return "DISCRETE";
  }
}
