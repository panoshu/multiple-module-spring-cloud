package com.example.file.domain.service;

import com.example.file.domain.model.enums.TriggerMatchType;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.RegionTrigger;
import com.example.file.domain.model.valueobject.parse.RawRow;

import java.util.List;
import java.util.regex.Pattern;

public class ParseContext {
  private final List<RegionDef> regions;
  private int currentRegionIdx = 0;

  public ParseContext(List<RegionDef> regions) {
    this.regions = regions;
  }

  public boolean isNextRegionTrigger(RawRow row) {
    if (currentRegionIdx + 1 >= regions.size()) return false;
    RegionDef next = regions.get(currentRegionIdx + 1);
    RegionTrigger trigger = next.trigger();
    if (trigger == null) return false;
    return matchesTrigger(row, trigger);
  }

  public void enterRegion(int idx) { this.currentRegionIdx = idx; }
  public int currentRegionIdx() { return currentRegionIdx; }

  private boolean matchesTrigger(RawRow row, RegionTrigger trigger) {
    if (row == null || row.isBlank()) return false;
    if (trigger.matchType() == TriggerMatchType.HEADER_SNIFF) {
      // 检查 cells 是否包含指纹中至少 minMatchCount 个匹配
      long matchCount = row.cells().values().stream()
          .filter(v -> v != null && !v.isBlank())
          .count();
      return matchCount >= trigger.minMatchCount();
    } else if (trigger.matchType() == TriggerMatchType.REGEX) {
      // 简化：第一个非空单元格匹配任意 regex
      return row.cells().values().stream().anyMatch(v -> v != null && !v.isBlank());
    }
    return false;
  }
}
