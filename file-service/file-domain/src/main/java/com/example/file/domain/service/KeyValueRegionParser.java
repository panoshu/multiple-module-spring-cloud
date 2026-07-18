package com.example.file.domain.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DomainService
public class KeyValueRegionParser implements RegionParser {

  @Override
  public RegionType supportedType() { return RegionType.KEY_VALUE; }

  @Override
  public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
    KvStrategy strategy = (KvStrategy) regionDef.strategy();
    Map<String, Object> data = new LinkedHashMap<>();
    int consecutiveBlank = 0;

    while (stream.hasNext()) {
      RawRow row = stream.peek();

      if (row.isBlank()) {
        if (++consecutiveBlank >= strategy.maxBlankRows()) break;
        stream.next();
        continue;
      }
      consecutiveBlank = 0;

      if (ctx.isNextRegionTrigger(row)) break;

      stream.next();
      Map<String, String> matched = matchLabels(row, strategy);
      data.putAll(matched);
    }

    return new KvRegionResult(regionDef.name(), data);
  }

  private Map<String, String> matchLabels(RawRow row, KvStrategy strategy) {
    Map<String, String> result = new LinkedHashMap<>();
    Map<Integer, String> cells = row.cells();

    for (Map.Entry<String, List<String>> entry : strategy.labelAliases().entrySet()) {
      String canonicalKey = entry.getKey();
      List<String> aliases = entry.getValue();

      for (Map.Entry<Integer, String> cell : cells.entrySet()) {
        int colIdx = cell.getKey();
        String cellValue = cell.getValue();
        if (aliases.contains(cellValue) && strategy.valuePosition() == KvValuePosition.RIGHT) {
          String value = cells.get(colIdx + 1);
          if (value != null) result.put(canonicalKey, value);
        }
        // BELOW 模式：值在下一行同列，由调用方提供后续行
      }
    }
    return result;
  }
}
