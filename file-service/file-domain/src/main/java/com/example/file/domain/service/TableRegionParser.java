package com.example.file.domain.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;

import java.util.*;

@DomainService
public class TableRegionParser implements RegionParser {

  @Override
  public RegionType supportedType() { return RegionType.TABLE; }

  @Override
  public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
    TableStrategy strategy = (TableStrategy) regionDef.strategy();
    List<String> headers = new ArrayList<>();
    List<Map<String, Object>> rows = new ArrayList<>();
    int dataRowCount = 0;
    int headerRowsRead = 0;

    while (stream.hasNext()) {
      RawRow row = stream.peek();
      if (row.isBlank()) { stream.next(); continue; }
      if (ctx.isNextRegionTrigger(row)) break;

      stream.next();
      if (headerRowsRead < strategy.headerRows()) {
        if (headerRowsRead == strategy.headerRows() - 1) {
          headers = extractHeaders(row, strategy);
        }
        headerRowsRead++;
        continue;
      }
      if (strategy.maxRows() > 0 && dataRowCount >= strategy.maxRows()) break;
      Map<String, Object> dataRow = mapDataRow(row, headers);
      if (!dataRow.isEmpty()) {
        rows.add(dataRow);
        dataRowCount++;
      }
    }
    return new TableRegionResult(regionDef.name(), headers, rows);
  }

  private List<String> extractHeaders(RawRow row, TableStrategy strategy) {
    List<String> headers = new ArrayList<>();
    Map<Integer, String> cells = row.cells();
    int maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    for (int i = 0; i <= maxColIdx; i++) {
      String cellValue = cells.get(i);
      if (cellValue == null || cellValue.isBlank()) { headers.add(null); continue; }
      String canonical = strategy.headerAliases().entrySet().stream()
          .filter(e -> e.getValue().contains(cellValue))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(HeaderMatching.STRICT.equals(strategy.headerMatching()) ? null : cellValue);
      headers.add(canonical);
    }
    return headers;
  }

  private Map<String, Object> mapDataRow(RawRow row, List<String> headers) {
    Map<String, Object> dataRow = new LinkedHashMap<>();
    Map<Integer, String> cells = row.cells();
    int maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    int maxIdx = Math.min(headers.size() - 1, maxColIdx);
    for (int i = 0; i <= maxIdx; i++) {
      String key = headers.get(i);
      if (key != null) dataRow.put(key, cells.get(i));
    }
    return dataRow;
  }
}
