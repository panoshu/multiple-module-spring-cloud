package com.example.file.infrastructure.gateway;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.KvStrategy;
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
            int colIndex = entry.getKey() + 1;
            String val = entry.getValue();
            cells.put(colIndex, val);
            if (val != null && !val.trim().isEmpty()) {
              isBlank = false;
            }
          }
        }
        rows.add(new RawRow(rowIndex, cells, isBlank));
      }

      @Override
      public void doAfterAllAnalysed(AnalysisContext context) {
      }
    }).sheet().doRead();
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
    int matchCount = 0;
    for (String cellValue : row.cells().values()) {
      if (cellValue != null && !cellValue.trim().isEmpty()) {
        matchCount++;
      }
    }
    return matchCount >= region.trigger().minMatchCount();
  }

  private KvRegionResult parseKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    Map<String, Object> data = new LinkedHashMap<>();
    KvStrategy strategy = (KvStrategy) region.strategy();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;

    int i = triggerRow + 1;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) {
        blankCount++;
        if (blankCount >= maxBlankRows) break;
        i++;
        continue;
      }
      blankCount = 0;

      String key = row.cells().get(1);
      String value = row.cells().get(2);
      if (key != null && !key.trim().isEmpty()) {
        data.put(key.trim(), value != null ? value.trim() : "");
      }
      i++;
    }

    return new KvRegionResult(region.name(), data);
  }

  private int advancePastKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    KvStrategy strategy = (KvStrategy) region.strategy();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;
    int i = triggerRow + 1;
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

    int headerStart = triggerRow + 1;
    List<String> headers = new ArrayList<>();
    RawRow headerRow = rows.get(headerStart);
    for (Map.Entry<Integer, String> entry : headerRow.cells().entrySet()) {
      headers.add(entry.getValue() != null ? entry.getValue().trim() : "");
    }

    List<Map<String, Object>> dataRows = new ArrayList<>();
    int maxRows = strategy.maxRows();
    int rowCount = 0;
    int i = headerStart + headerRows;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;

      Map<String, Object> rowData = new LinkedHashMap<>();
      for (int col = 1; col <= headers.size(); col++) {
        String header = headers.get(col - 1);
        if (header != null && !header.isEmpty()) {
          String val = row.cells().get(col);
          rowData.put(header, val != null ? val.trim() : "");
        }
      }
      dataRows.add(rowData);
      rowCount++;
      i++;
    }

    return new TableRegionResult(region.name(), headers, dataRows);
  }

  private int advancePastTableRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    TableStrategy strategy = (TableStrategy) region.strategy();
    int headerRows = strategy.headerRows();
    int maxRows = strategy.maxRows();
    int headerStart = triggerRow + 1;
    int i = headerStart + headerRows;
    int rowCount = 0;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;
      rowCount++;
      i++;
    }
    return i;
  }
}
