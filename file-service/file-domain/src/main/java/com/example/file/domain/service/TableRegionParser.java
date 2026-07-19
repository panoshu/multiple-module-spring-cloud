package com.example.file.domain.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
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
    int nameRowIdx = strategy.headerNameRow() == 0
        ? strategy.headerRows() - 1
        : strategy.headerNameRow() - 1;

    while (stream.hasNext()) {
      RawRow row = stream.peek();
      if (row.isBlank()) { stream.next(); continue; }

      if (headerRowsRead < strategy.headerRows()) {
        // 表头阶段：不检查 isNextRegionTrigger，因为表头行本身可能匹配下一个 region 的 trigger
        // （例如 filler_info 的 HEADER_SNIFF(1) 会被任何非空行匹配）
        stream.next();
        if (headerRowsRead == nameRowIdx) {
          headers = extractHeaders(row, strategy);
        }
        headerRowsRead++;
        continue;
      }
      // 数据阶段：先检查再消费，trigger 行 / dataEnd 行留给下一个 region 或跳过
      if (ctx.isNextRegionTrigger(row)) break;
      if (strategy.maxRows() > 0 && dataRowCount >= strategy.maxRows()) break;
      if (isDataEnd(row, strategy.dataEnd())) break;
      stream.next();
      Map<String, Object> dataRow = mapDataRow(row, headers);
      if (!dataRow.isEmpty()) {
        rows.add(dataRow);
        dataRowCount++;
      }
    }
    return new TableRegionResult(regionDef.name(), headers, rows);
  }

  private boolean isDataEnd(RawRow row, DataEndRule dataEnd) {
    if (dataEnd == null || dataEnd.markers().isEmpty()) return false;
    String firstCell = row.cells().get(0);
    if (firstCell == null) return false;
    return dataEnd.markers().contains(firstCell.trim());
  }

  private List<String> extractHeaders(RawRow row, TableStrategy strategy) {
    List<String> headers = new ArrayList<>();
    Map<Integer, String> cells = row.cells();
    int maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    for (int i = 0; i <= maxColIdx; i++) {
      String cellValue = cells.get(i);
      if (cellValue == null || cellValue.isBlank()) {
        // TableRegionResult 通过 List.copyOf 拒绝 null，用空串占位以保留列对齐
        headers.add("");
        continue;
      }
      String canonical = strategy.headerAliases().entrySet().stream()
          .filter(e -> e.getValue().contains(cellValue))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(HeaderMatching.STRICT.equals(strategy.headerMatching()) ? "" : cellValue);
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
      // 跳过空 header（null 或空串），不映射该列
      if (key != null && !key.isEmpty()) dataRow.put(key, cells.get(i));
    }
    return dataRow;
  }
}
