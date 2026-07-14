// 文件: src/main/java/infrastructure/excel/EasyExcelOutboundAdapter.java
package infrastructure.excel;

import com.alibaba.excel.EasyExcel;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import core.domain.model.*;
import core.domain.outbound.OutboundAdapterPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.HeaderMapping;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class EasyExcelOutboundAdapter implements OutboundAdapterPort {

  @Override
  public boolean supports(ExportEngineType engineType) {
    return engineType == ExportEngineType.STREAM;
  }

  @Override
  public void write(OutboundTemplate outboundTemplate, String jsonPayload, OutputStream outputStream) {
    OutboundRule rule = outboundTemplate.outboundRule();
    DocumentContext jsonContext = JsonPath.parse(jsonPayload);
    Map<Integer, Map<Integer, Object>> virtualExcel = new TreeMap<>();

    // 1. 渲染静态文本 (增加 null 保护)
    if (rule.staticTexts() != null) {
      for (StaticTextMapping stm : rule.staticTexts()) {
        int r = CoordinateUtils.getRowIndex(stm.cell());
        int c = CoordinateUtils.getColIndex(stm.cell());
        virtualExcel.computeIfAbsent(r, k -> new TreeMap<>()).put(c, stm.text());
      }
    }

    // 2. 渲染动态表头 HeaderZone (增加 null 保护)
    if (rule.headerZone() != null && rule.headerZone().fields() != null) {
      for (HeaderMapping mapping : rule.headerZone().fields()) {
        try {
          Object value = jsonContext.read(mapping.jsonPath());
          int r = CoordinateUtils.getRowIndex(mapping.cell());
          int c = CoordinateUtils.getColIndex(mapping.cell());
          virtualExcel.computeIfAbsent(r, k -> new TreeMap<>()).put(c, value);
        } catch (Exception e) {
          // 忽略 JSON 中缺失的值
        }
      }
    }

    // 3. 渲染明细 DetailZone (增加 null 保护)
    DetailZone detailZone = rule.detailZone();
    if (detailZone != null && detailZone.fields() != null) {
      // 3.1 渲染多行表头 (英文ID 与 中文标题)
      int idRowIndex = detailZone.fieldIdRow() - 1;
      int titleRowIndex = detailZone.titleRow() - 1;

      for (DetailMapping mapping : detailZone.fields()) {
        int colIndex = CoordinateUtils.colNameToIndex(mapping.col());

        if (detailZone.fieldIdRow() > 0 && mapping.fieldId() != null) {
          virtualExcel.computeIfAbsent(idRowIndex, k -> new TreeMap<>())
            .put(colIndex, mapping.fieldId());
        }
        if (detailZone.titleRow() > 0 && mapping.exportTitle() != null) {
          virtualExcel.computeIfAbsent(titleRowIndex, k -> new TreeMap<>())
            .put(colIndex, mapping.exportTitle());
        }
      }

      // 3.2 渲染真实明细数据列表
      int currentRow = detailZone.startRow() - 1;
      String arrayPath = extractArrayPath(detailZone.fields().get(0).jsonPath());
      Integer listSize = jsonContext.read(arrayPath + ".length()");

      if (listSize != null && listSize > 0) {
        for (int i = 0; i < listSize; i++) {
          for (DetailMapping mapping : detailZone.fields()) {
            String exactPath = mapping.jsonPath().replace("[*]", "[" + i + "]");
            try {
              Object value = jsonContext.read(exactPath);
              int colIndex = CoordinateUtils.colNameToIndex(mapping.col());
              virtualExcel.computeIfAbsent(currentRow, k -> new TreeMap<>())
                .put(colIndex, value);
            } catch (Exception e) {
              // 忽略单个值的缺失
            }
          }
          currentRow++;
        }
      }
    }

    // 4. 将虚拟网格写入物理文件
    List<List<Object>> rawSheetData = flushToSheet(virtualExcel);
    EasyExcel.write(outputStream).sheet("Sheet1").doWrite(rawSheetData);
  }

  // 辅助方法：将 TreeMap 网格转换为 EasyExcel 需要的 List<List>
  private List<List<Object>> flushToSheet(Map<Integer, Map<Integer, Object>> virtualExcel) {
    List<List<Object>> sheetData = new ArrayList<>();
    if (virtualExcel.isEmpty()) {
      return sheetData;
    }

    int maxRow = virtualExcel.keySet().stream().max(Integer::compareTo).orElse(0);
    int maxCol = virtualExcel.values().stream()
      .flatMap(map -> map.keySet().stream())
      .max(Integer::compareTo).orElse(0);

    for (int r = 0; r <= maxRow; r++) {
      List<Object> rowData = new ArrayList<>();
      Map<Integer, Object> rowMap = virtualExcel.get(r);
      for (int c = 0; c <= maxCol; c++) {
        rowData.add(rowMap != null ? rowMap.get(c) : null);
      }
      sheetData.add(rowData);
    }
    return sheetData;
  }

  private String extractArrayPath(String fullPath) {
    int index = fullPath.indexOf("[*]");
    return index > 0 ? fullPath.substring(0, index) : fullPath;
  }
}
