package com.example.file.domain.model.parse.valueobject;

public sealed interface RegionParseResult permits KvRegionResult, TableRegionResult, RegionSkip {
  String regionName();
}
