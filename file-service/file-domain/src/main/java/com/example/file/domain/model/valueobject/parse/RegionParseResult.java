package com.example.file.domain.model.valueobject.parse;

public sealed interface RegionParseResult permits KvRegionResult, TableRegionResult, RegionSkip {
  String regionName();
}
