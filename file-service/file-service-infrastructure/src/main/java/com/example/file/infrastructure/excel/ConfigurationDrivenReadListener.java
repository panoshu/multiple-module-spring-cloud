package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.Direction;
import com.example.file.domain.model.locator.AbsoluteLocator;
import com.example.file.domain.model.locator.AnchorRelativeLocator;
import com.example.file.domain.model.locator.RegionRelativeLocator;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.region.RegionConfig;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.TableMeta;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 备用监听器：同样接入“画布流状态机”，支持旧版严格配置模式
 */
public class ConfigurationDrivenReadListener extends AnalysisEventListener<Map<Integer, String>> {

  private final Consumer<DataRow> rowProcessor;
  private final List<RegionConfig> orderedRegions;
  private final Map<String, Object> sharedDiscreteData = new HashMap<>();

  // 🟢 画布流状态机指针
  private int currentRegionIndex = 0;
  private int currentRegionStartPhysicalRow = 0;

  public ConfigurationDrivenReadListener(ExcelSchema schema, Consumer<DataRow> rowProcessor) {
    this.rowProcessor = rowProcessor;
    this.orderedRegions = schema.regions();
  }

  @Override
  public void invoke(Map<Integer, String> cellDataMap, AnalysisContext context) {
    if (currentRegionIndex >= orderedRegions.size()) {
      return;
    }

    int physicalRowIndex = context.readRowHolder().getRowIndex(); // 0-based
    RegionConfig currentRegion = orderedRegions.get(currentRegionIndex);
    int relativeRow = physicalRowIndex - currentRegionStartPhysicalRow;

    switch (currentRegion) {
      case DiscreteRegionConfig discrete -> {
        // 🟢 兼容新老定位器的提取逻辑
        extractDiscreteData(discrete, cellDataMap, relativeRow, physicalRowIndex);

        if (relativeRow >= discrete.rows() - 1) {
          moveToNextRegion(physicalRowIndex + 1);
        }
      }
      case HorizontalTableRegionConfig table -> {
        TableMeta meta = table.tableMeta();
        int targetDataRow = meta.dataStartRow() - 1;

        if (relativeRow >= targetDataRow) {
          if (meta.endMarker() != null && !meta.endMarker().isBlank()) {
            boolean hitMarker = cellDataMap.values().stream().anyMatch(val -> val != null && val.contains(meta.endMarker()));
            if (hitMarker) {
              moveToNextRegion(physicalRowIndex + 1);
              return;
            }
          }

          Map<String, Object> rowData = new HashMap<>(sharedDiscreteData);
          boolean hasData = false;

          // 🟢 修复：支持横表列通过 RegionRelativeLocator (提取对应列的数据)
          for (FieldConfig field : table.fields()) {
            if (field.locator() instanceof RegionRelativeLocator rrl) {
              extractAndConvert(field, cellDataMap.get(rrl.col() - 1), rowData);
              hasData = true;
            } else if (field.locator() instanceof AbsoluteLocator al) {
              int[] coords = resolveCellCoordinates(al.cell());
              extractAndConvert(field, cellDataMap.get(coords[1]), rowData);
              hasData = true;
            }
          }

          if (hasData) {
            rowProcessor.accept(new DataRow(physicalRowIndex + 1, rowData));
          }
        }
      }
      default -> {
      }
    }
  }

  private void moveToNextRegion(int nextPhysicalStartRow) {
    currentRegionIndex++;
    currentRegionStartPhysicalRow = nextPhysicalStartRow;
  }

  private void extractDiscreteData(DiscreteRegionConfig discrete, Map<Integer, String> cellDataMap, int relativeRow, int physicalRow) {
    for (FieldConfig field : discrete.fields()) {
      int targetCol = -1;
      boolean matchRow = false;

      // 🟢 核心重构：多态路由新老定位器
      switch (field.locator()) {
        case RegionRelativeLocator rrl -> {
          if (relativeRow == rrl.row() - 1) {
            targetCol = rrl.col() - 1;
            matchRow = true;
          }
        }
        case AbsoluteLocator al -> {
          int[] coords = resolveCellCoordinates(al.cell());
          // 绝对定位器匹配真实的全局物理行
          if (physicalRow == coords[0]) {
            targetCol = coords[1];
            matchRow = true;
          }
        }
        case AnchorRelativeLocator arl -> {
          // 锚点定位器在当前行寻找关键字
          for (Map.Entry<Integer, String> entry : cellDataMap.entrySet()) {
            if (arl.anchorText().equals(entry.getValue())) {
              targetCol = entry.getKey() + (arl.direction() == Direction.RIGHT ? arl.offset() : 0);
              matchRow = true;
              break;
            }
          }
        }
        default -> {
        }
      }

      if (matchRow && targetCol != -1) {
        extractAndConvert(field, cellDataMap.get(targetCol), sharedDiscreteData);
      }
    }
  }

  private void extractAndConvert(FieldConfig field, String rawValue, Map<String, Object> targetMap) {
    if (rawValue == null || rawValue.isBlank()) {
      return;
    }
    Object convertedValue = switch (field.dataType()) {
      case STRING -> rawValue.trim();
      case NUMBER -> new BigDecimal(rawValue.replace(",", ""));
      case DATE -> LocalDate.parse(rawValue, DateTimeFormatter.ofPattern(field.format()));
      case BOOLEAN -> Boolean.parseBoolean(rawValue);
    };
    targetMap.put(field.jsonKey(), convertedValue);
  }

  private int[] resolveCellCoordinates(String cellRef) {
    int col = cellRef.charAt(0) - 'A';
    int row = Integer.parseInt(cellRef.substring(1)) - 1;
    return new int[]{row, col};
  }

  @Override
  public void doAfterAllAnalysed(AnalysisContext context) {
  }
}
