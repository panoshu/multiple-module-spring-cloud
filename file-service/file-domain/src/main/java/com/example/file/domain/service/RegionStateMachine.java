package com.example.file.domain.service;

import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.shared.domain.annotation.DomainService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DomainService
public class RegionStateMachine {

  private final Map<RegionType, RegionParser> parsers;

  public RegionStateMachine(Map<RegionType, RegionParser> parsers) {
    this.parsers = parsers;
  }

  public List<RegionParseResult> drive(RawRowStream stream, List<RegionDef> regions, ParseContext ctx) {
    List<RegionParseResult> results = new ArrayList<>();
    int regionIdx = 0;

    while (stream.hasNext() && regionIdx < regions.size()) {
      RawRow current = stream.peek();
      RegionDef target = regions.get(regionIdx);

      if (shouldEnterRegion(current, target, ctx)) {
        ctx.enterRegion(regionIdx);
        RegionParser parser = parsers.get(target.type());
        if (parser == null) {
          throw new IllegalStateException("No parser for region type: " + target.type());
        }
        RegionParseResult result = parser.parse(stream, target, ctx);
        results.add(result);
        regionIdx++;
      } else {
        stream.next();
      }
    }
    return results;
  }

  private boolean shouldEnterRegion(RawRow row, RegionDef target, ParseContext ctx) {
    // 第一个区域或无 trigger 时立即进入
    if (target.trigger() == null) return true;
    // 否则检查当前行是否匹配 trigger
    return ctx.isNextRegionTrigger(row);
  }
}
