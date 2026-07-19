package com.example.file.domain.service;

import com.example.file.domain.model.enums.TriggerMatchType;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.RegionStrategy;
import com.example.file.domain.model.valueobject.config.RegionTrigger;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.RawRow;

import java.util.ArrayList;
import java.util.List;

public class ParseContext {
  private final List<RegionDef> regions;
  // 初始为 -1 表示"尚未进入任何 region"。
  // isNextRegionTrigger 检查 regions.get(currentRegionIdx + 1)：
  //   - 初始 -1 + 1 = 0，检查第一个 region 的 trigger（用于 RegionStateMachine.shouldEnterRegion 判断是否进入第一个 region）
  //   - enterRegion(idx) 后检查 regions.get(idx + 1)，即下一个 region 的 trigger（用于 KeyValueRegionParser/TableRegionParser 判断是否停止当前 region）
  private int currentRegionIdx = -1;

  public ParseContext(List<RegionDef> regions) {
    this.regions = regions;
  }

  public boolean isNextRegionTrigger(RawRow row) {
    int nextIdx = currentRegionIdx + 1;
    if (nextIdx >= regions.size()) return false;
    RegionDef next = regions.get(nextIdx);
    RegionTrigger trigger = next.trigger();
    if (trigger == null) return false;
    return matchesTrigger(row, trigger, next);
  }

  public void enterRegion(int idx) { this.currentRegionIdx = idx; }
  public int currentRegionIdx() { return currentRegionIdx; }

  private boolean matchesTrigger(RawRow row, RegionTrigger trigger, RegionDef region) {
    if (row == null || row.isBlank()) return false;
    if (trigger.matchType() == TriggerMatchType.HEADER_SNIFF) {
      // 优先使用 region strategy 中的指纹值（labelAliases / headerAliases）做精确匹配，
      // 避免数据行（非空单元格数多但不含表头/标签字面量）误触发下一个 region。
      List<String> fingerprints = extractFingerprints(region);
      if (!fingerprints.isEmpty()) {
        int required = Math.min(trigger.minMatchCount(), Math.max(1, fingerprints.size()));
        long matchCount = row.cells().values().stream()
            .filter(v -> v != null && fingerprints.contains(v.trim()))
            .count();
        return matchCount >= required;
      }
      // 无指纹时退化为非空单元格数检查
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

  private List<String> extractFingerprints(RegionDef region) {
    RegionStrategy strategy = region.strategy();
    if (strategy instanceof KvStrategy kv) {
      List<String> all = new ArrayList<>();
      kv.labelAliases().values().forEach(all::addAll);
      return all;
    } else if (strategy instanceof TableStrategy table) {
      List<String> all = new ArrayList<>();
      table.headerAliases().values().forEach(all::addAll);
      return all;
    }
    return List.of();
  }
}
