package com.example.file.infrastructure.gateway;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import com.example.file.domain.model.valueobject.parse.RegionSkip;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class ExcelParserImpl implements ExcelParser {

  @Override
  public RawRowStream openStream(InputStream excelStream) {
    List<RawRow> rows = readAllRows(excelStream);
    return new ListBackedRawRowStream(rows);
  }

  @Override
  public List<RegionParseResult> parse(InputStream excelStream, List<RegionDef> regions) {
    List<RawRow> allRows = readAllRows(excelStream);
    List<RegionParseResult> results = new ArrayList<>();
    int cursor = 0;

    for (RegionDef region : regions) {
      int triggerRow = findTriggerRow(allRows, cursor, region);
      if (triggerRow < 0) {
        results.add(new RegionSkip());
        continue;
      }

      if (region.type() == RegionType.KEY_VALUE) {
        KvRegionResult kvResult = parseKvRegion(allRows, triggerRow, region);
        results.add(kvResult);
        cursor = advancePastKvRegion(allRows, triggerRow, region);
      } else if (region.type() == RegionType.TABLE) {
        TableRegionResult tableResult = parseTableRegion(allRows, triggerRow, region);
        results.add(tableResult);
        cursor = advancePastTableRegion(allRows, triggerRow, region);
      } else {
        results.add(new RegionSkip());
      }
    }

    return results;
  }

  private List<RawRow> readAllRows(InputStream is) {
    List<RawRow> rows = new ArrayList<>();
    FesodSheet.read(is, new ReadListener<Map<Integer, String>>() {
      @Override
      public void invoke(Map<Integer, String> data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;
        Map<Integer, String> cells = new HashMap<>();
        boolean isBlank = true;
        if (data != null) {
          for (Map.Entry<Integer, String> entry : data.entrySet()) {
            // 0-based 列索引，与 domain 层一致
            int colIndex = entry.getKey();
            String val = entry.getValue();
            // RawRow 通过 Map.copyOf 拒绝 null value，此处跳过 null 单元格
            if (val == null) continue;
            cells.put(colIndex, val);
            if (!val.trim().isEmpty()) {
              isBlank = false;
            }
          }
        }
        rows.add(new RawRow(rowIndex, cells, isBlank));
      }

      @Override
      public void doAfterAllAnalysed(AnalysisContext context) {
      }
    }).sheet().headRowNumber(0).doRead();
    return rows;
  }

  private int findTriggerRow(List<RawRow> rows, int startIndex, RegionDef region) {
    if (region.trigger() == null) {
      return startIndex;
    }
    for (int i = startIndex; i < rows.size(); i++) {
      RawRow row = rows.get(i);
      if (matchesTrigger(row, region)) {
        return i;
      }
    }
    return -1;
  }

  private boolean matchesTrigger(RawRow row, RegionDef region) {
    if (region.trigger() == null) return true;
    if (row.isBlank()) return false;
    long matchCount = row.cells().values().stream()
        .filter(v -> v != null && !v.trim().isEmpty())
        .count();
    return matchCount >= region.trigger().minMatchCount();
  }

  private KvRegionResult parseKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    KvStrategy strategy = (KvStrategy) region.strategy();
    Map<String, Object> data = new LinkedHashMap<>();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;

    int i = triggerRow;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) {
        blankCount++;
        if (blankCount >= maxBlankRows) break;
        i++;
        continue;
      }
      blankCount = 0;

      // 基于 labelAliases 匹配，支持每行多组 KV
      Map<Integer, String> cells = row.cells();
      for (Map.Entry<String, List<String>> entry : strategy.labelAliases().entrySet()) {
        String canonicalKey = entry.getKey();
        List<String> aliases = entry.getValue();
        for (Map.Entry<Integer, String> cell : cells.entrySet()) {
          int colIdx = cell.getKey();
          String cellValue = cell.getValue();
          if (aliases.contains(cellValue) && strategy.valuePosition() == KvValuePosition.RIGHT) {
            String value = cells.get(colIdx + 1);
            if (value != null) {
              data.put(canonicalKey, value.trim());
            }
          }
        }
      }
      i++;
    }

    return new KvRegionResult(region.name(), data);
  }

  private int advancePastKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    KvStrategy strategy = (KvStrategy) region.strategy();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;
    int i = triggerRow;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) {
        blankCount++;
        if (blankCount >= maxBlankRows) break;
      } else {
        blankCount = 0;
      }
      i++;
    }
    return i;
  }

  private TableRegionResult parseTableRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    TableStrategy strategy = (TableStrategy) region.strategy();
    int headerRows = strategy.headerRows();
    int nameRowIdx = strategy.headerNameRow() == 0
        ? headerRows - 1
        : strategy.headerNameRow() - 1;

    // HEADER_SNIFF 触发的行本身即为第一行表头，因此 headerStart = triggerRow
    // （原 brief 为 triggerRow + 1，会跳过含 XH/XM 等代码表头行，导致 headerAliases 无法匹配）
    int headerStart = triggerRow;
    List<String> headers = new ArrayList<>();
    int maxColIdx = 0;

    // 读取表头行
    for (int h = 0; h < headerRows; h++) {
      int rowIdx = headerStart + h;
      if (rowIdx >= rows.size()) break;
      if (h == nameRowIdx) {
        RawRow headerRow = rows.get(rowIdx);
        Map<Integer, String> cells = headerRow.cells();
        maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int col = 0; col <= maxColIdx; col++) {
          String cellValue = cells.get(col);
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
      }
    }

    // 读取数据行
    List<Map<String, Object>> dataRows = new ArrayList<>();
    int maxRows = strategy.maxRows();
    int rowCount = 0;
    int i = headerStart + headerRows;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;
      if (isDataEnd(row, strategy.dataEnd())) break;

      Map<String, Object> rowData = new LinkedHashMap<>();
      Map<Integer, String> cells = row.cells();
      for (int col = 0; col <= Math.min(headers.size() - 1, maxColIdx); col++) {
        String header = headers.get(col);
        if (header != null && !header.isEmpty()) {
          String val = cells.get(col);
          rowData.put(header, val != null ? val.trim() : "");
        }
      }
      if (!rowData.isEmpty()) {
        dataRows.add(rowData);
        rowCount++;
      }
      i++;
    }

    return new TableRegionResult(region.name(), headers, dataRows);
  }

  private boolean isDataEnd(RawRow row, DataEndRule dataEnd) {
    if (dataEnd == null || dataEnd.markers().isEmpty()) return false;
    String firstCell = row.cells().get(0);
    if (firstCell == null) return false;
    return dataEnd.markers().contains(firstCell.trim());
  }

  private int advancePastTableRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    TableStrategy strategy = (TableStrategy) region.strategy();
    int headerRows = strategy.headerRows();
    int maxRows = strategy.maxRows();
    int headerStart = triggerRow;
    int i = headerStart + headerRows;
    int rowCount = 0;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;
      if (isDataEnd(row, strategy.dataEnd())) break;
      rowCount++;
      i++;
    }
    return i;
  }
}
