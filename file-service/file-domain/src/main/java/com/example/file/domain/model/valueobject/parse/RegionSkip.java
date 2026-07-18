package com.example.file.domain.model.valueobject.parse;

public record RegionSkip() implements RegionParseResult {
  @Override
  public String regionName() { return ""; }
}
