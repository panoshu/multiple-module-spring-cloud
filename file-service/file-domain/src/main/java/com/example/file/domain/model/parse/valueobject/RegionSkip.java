package com.example.file.domain.model.parse.valueobject;

public record RegionSkip() implements RegionParseResult {
  @Override
  public String regionName() {
    return "";
  }
}
