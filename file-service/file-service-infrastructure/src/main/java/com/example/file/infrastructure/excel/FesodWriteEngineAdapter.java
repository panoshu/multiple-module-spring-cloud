package com.example.file.infrastructure.excel;

import com.example.file.domain.gateway.ExcelWriteEnginePort;
import com.example.file.domain.model.enums.DynamicFieldPolicy;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.locator.AbsoluteLocator;
import com.example.file.domain.model.locator.HeaderMatchLocator;
import com.example.file.domain.model.locator.RegionRelativeLocator;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.region.RegionConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.TableMeta;
import org.apache.fesod.sheet.FesodSheet;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FesodWriteEngineAdapter implements ExcelWriteEnginePort {

  @Override
  public void writeExcel(OutputStream out, ExcelSchema schema, Map<String, Object> discreteData, List<Map<String, Object>> tableData) {
    // 🟢 超级大矩阵：将所有区域拍平，直接作为最纯净的流式数据冲刷出去
    List<List<Object>> globalCanvasMatrix = new ArrayList<>();

    for (RegionConfig region : schema.regions()) {
      switch (region) {
        case DiscreteRegionConfig discrete -> {
          globalCanvasMatrix.addAll(renderDiscreteToMatrix(discrete, discreteData));
        }
        case HorizontalTableRegionConfig tableRegion -> {
          globalCanvasMatrix.addAll(renderTableToMatrix(tableRegion, tableData));
        }
        default -> {
        }
      }
    }

    // 🟢 降维打击：不使用 Fesod 的表头体系，全局矩阵接管一切
    FesodSheet.write(out)
      .sheet("员工入职清单")
      .doWrite(globalCanvasMatrix);
  }

  private List<List<Object>> renderDiscreteToMatrix(DiscreteRegionConfig discrete, Map<String, Object> discreteData) {
    int height = discrete.rows();

    List<List<Object>> matrix = new ArrayList<>();
    for (int i = 0; i < height; i++) {
      List<Object> row = new ArrayList<>();
      for (int j = 0; j < 50; j++) row.add(null); // 初始化较宽列占位符
      matrix.add(row);
    }

    for (FieldConfig field : discrete.fields()) {
      int targetRow = -1;
      int targetCol = -1;

      // 多态兼容新老写入定位器
      switch (field.locator()) {
        case RegionRelativeLocator rrl -> {
          targetRow = rrl.row() - 1;
          targetCol = rrl.col() - 1;
        }
        case AbsoluteLocator al -> {
          // 将 "A1" 转换为 0-based 坐标
          int[] coords = resolveCellCoordinates(al.cell());
          targetRow = coords[0];
          targetCol = coords[1];
        }
        default -> {
        }
      }

      if (targetRow >= 0 && targetRow < height) {
        String content = "";
        if (field.fieldType() == FieldType.TEXT_FIELD) {
          content = field.label();
        } else if (field.fieldType() == FieldType.DATA_FIELD) {
          content = String.valueOf(discreteData.getOrDefault(field.jsonKey(), ""));
        }

        // 自动扩容防越界 (万一用户配置的列数超过初始化的 50 列)
        while (matrix.get(targetRow).size() <= targetCol) {
          matrix.get(targetRow).add(null);
        }

        matrix.get(targetRow).set(targetCol, content);
      }
    }
    return matrix;
  }

  private List<List<Object>> renderTableToMatrix(HorizontalTableRegionConfig tableRegion, List<Map<String, Object>> tableData) {
    TableMeta meta = tableRegion.tableMeta();
    List<FieldConfig> fields = tableRegion.fields();

    int targetIdRow = meta.idRowIndex() - 1;
    int targetNameRow = meta.nameRowIndex() - 1;
    int targetDataRow = meta.dataStartRow() - 1;
    int targetDataCol = meta.dataStartCol() - 1;

    // 初始化表格头部的骨架 (一直拉伸到 dataStartRow 之前)
    List<List<Object>> matrix = new ArrayList<>();
    for (int i = 0; i < targetDataRow; i++) {
      List<Object> row = new ArrayList<>();
      for (int j = 0; j < 50; j++) row.add(null);
      matrix.add(row);
    }

    List<String> dataKeys = new ArrayList<>();
    int currentColOffset = targetDataCol;

    // 1. 画强类型配置表头
    for (FieldConfig field : fields) {
      if (field.locator() instanceof HeaderMatchLocator hml) {
        autoExpandRow(matrix.get(targetIdRow), currentColOffset);
        autoExpandRow(matrix.get(targetNameRow), currentColOffset);
        matrix.get(targetIdRow).set(currentColOffset, hml.matchId());
        matrix.get(targetNameRow).set(currentColOffset, hml.matchName());
        dataKeys.add(field.jsonKey());
        currentColOffset++;
      }
    }

    // 2. 画弱类型动态追加表头
    if (meta.allowDynamicFields() && meta.dynamicFieldPolicy() == DynamicFieldPolicy.AUTO_APPEND && !tableData.isEmpty()) {
      for (String key : tableData.getFirst().keySet()) {
        if (key.startsWith(meta.dynamicFieldInternalIdPrefix())) {
          autoExpandRow(matrix.get(targetIdRow), currentColOffset);
          autoExpandRow(matrix.get(targetNameRow), currentColOffset);
          matrix.get(targetIdRow).set(currentColOffset, "EXT_ID");
          matrix.get(targetNameRow).set(currentColOffset, key.replace(meta.dynamicFieldInternalIdPrefix(), ""));
          dataKeys.add(key);
          currentColOffset++;
        }
      }
    }

    // 3. 画明细数据
    for (Map<String, Object> mapRow : tableData) {
      List<Object> dataRow = new ArrayList<>();
      for (int i = 0; i < targetDataCol; i++) dataRow.add(null); // 左侧偏移
      for (String key : dataKeys) {
        dataRow.add(mapRow.get(key));
      }
      matrix.add(dataRow);
    }

    // 4. 追加尾注截断符 (EndMarker)
    if (meta.endMarker() != null && !meta.endMarker().isBlank()) {
      List<Object> markerRow = new ArrayList<>();
      markerRow.add(meta.endMarker());
      matrix.add(markerRow);
    }

    return matrix;
  }

  // ==========================================
  // 辅助工具方法
  // ==========================================

  /**
   * 解析绝对坐标 (如 "A1", "AA1") 转为 0-based 数组 [row, col]
   */
  private int[] resolveCellCoordinates(String cellRef) {
    String upper = cellRef.trim().toUpperCase();
    int firstDigitIdx = 0;
    for (int i = 0; i < upper.length(); i++) {
      if (Character.isDigit(upper.charAt(i))) {
        firstDigitIdx = i;
        break;
      }
    }

    // 提取字母部分(列)和数字部分(行)
    String colPart = upper.substring(0, firstDigitIdx);
    int rowPart = Integer.parseInt(upper.substring(firstDigitIdx));

    // 处理多字母列 (如 AA)
    int colIndex = 0;
    for (int i = 0; i < colPart.length(); i++) {
      colIndex = colIndex * 26 + (colPart.charAt(i) - 'A' + 1);
    }

    return new int[]{rowPart - 1, colIndex - 1};
  }

  /**
   * 自动扩容 List，防止向极大的列索引 set 数据时报 IndexOutOfBoundsException
   */
  private void autoExpandRow(List<Object> row, int targetColIndex) {
    while (row.size() <= targetColIndex) {
      row.add(null);
    }
  }
}
