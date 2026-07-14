package com.example.file.infrastructure.excel.persistence;

import com.example.file.domain.model.enums.DynamicFieldPolicy;
import com.example.file.domain.model.locator.HeaderMatchLocator;
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
import java.util.*;
import java.util.function.Consumer;

public class DynamicSchemaEventListener extends AnalysisEventListener<Map<Integer, String>> {

  private final Consumer<DataRow> rowProcessor;
  private final List<RegionConfig> orderedRegions;

  private final Map<String, Object> sharedDiscreteData = new HashMap<>();
  private final Map<Integer, FieldConfig> currentTableColumnMapping = new HashMap<>();
  private final Set<FieldConfig> idMatchedFields = new HashSet<>();

  // 🟢 画布流状态机指针
  private int currentRegionIndex = 0;
  private int currentRegionStartPhysicalRow = 0; // 当前区域在真实 Excel 中的起始物理行 (0-based)

  public DynamicSchemaEventListener(ExcelSchema schema, Consumer<DataRow> rowProcessor) {
    this.rowProcessor = rowProcessor;
    this.orderedRegions = schema.regions(); // 严格按照 YAML 中配置的顺序
  }

  @Override
  public void invoke(Map<Integer, String> cellDataMap, AnalysisContext context) {
    if (currentRegionIndex >= orderedRegions.size()) {
      return; // 所有的区域都已经扫完了
    }

    int physicalRowIndex = context.readRowHolder().getRowIndex();
    RegionConfig currentRegion = orderedRegions.get(currentRegionIndex);

    // 当前行相对于当前激活区域的局部相对行号 (0-based)
    int relativeRow = physicalRowIndex - currentRegionStartPhysicalRow;

    switch (currentRegion) {
      case DiscreteRegionConfig discrete -> {
        extractDiscreteData(discrete, cellDataMap, relativeRow);
        // 🟢 如果已经到达该离散区域的最后一行，指针向下移动，激活下一个区域
        if (relativeRow >= discrete.rows() - 1) {
          moveToNextRegion(physicalRowIndex + 1);
        }
      }
      case HorizontalTableRegionConfig tableRegion -> {
        TableMeta meta = tableRegion.tableMeta();

        // 🟢 人类配置 (1-based) 转底层机器相对坐标 (0-based)
        int targetIdRow = meta.idRowIndex() - 1;
        int targetNameRow = meta.nameRowIndex() - 1;
        int targetDataRow = meta.dataStartRow() - 1;
        int targetDataCol = meta.dataStartCol() - 1;

        // 判断是否触发表格底部的结束标记 (EndMarker 拦截)
        if (relativeRow >= targetDataRow && meta.endMarker() != null && !meta.endMarker().isBlank()) {
          boolean hitMarker = cellDataMap.values().stream()
            .anyMatch(val -> val != null && val.contains(meta.endMarker()));
          if (hitMarker) {
            // 发现尾注，表格彻底结束，立即激活下一个区域
            moveToNextRegion(physicalRowIndex + 1);
            return; // 当前行是尾注，不再作为表格数据下发
          }
        }

        if (relativeRow == targetIdRow || relativeRow == targetNameRow) {
          buildTableColumnMapping(tableRegion, cellDataMap, relativeRow, targetIdRow, targetNameRow);
          return;
        }

        if (relativeRow >= targetDataRow) {
          Map<String, Object> rowData = new HashMap<>(sharedDiscreteData);
          boolean hasValidTableData = false;

          for (Map.Entry<Integer, String> entry : cellDataMap.entrySet()) {
            Integer colIndex = entry.getKey();
            if (colIndex < targetDataCol) {
              continue; // 🟢 剔除起始列之前的数据
            }

            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
              continue;
            }

            FieldConfig config = currentTableColumnMapping.get(colIndex);
            if (config != null) {
              rowData.put(config.jsonKey(), parseValue(rawValue, config));
              hasValidTableData = true;
            } else if (meta.allowDynamicFields() && meta.dynamicFieldPolicy() == DynamicFieldPolicy.AUTO_APPEND) {
              String dynKey = meta.dynamicFieldInternalIdPrefix() + "COL_" + colIndex;
              rowData.put(dynKey, rawValue);
              hasValidTableData = true;
            }
          }

          if (hasValidTableData) {
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
    currentTableColumnMapping.clear();
    idMatchedFields.clear();
  }

  private void extractDiscreteData(DiscreteRegionConfig discrete, Map<Integer, String> cellDataMap, int relativeRowIndex) {
    for (FieldConfig field : discrete.fields()) {
      if (field.locator() instanceof RegionRelativeLocator rrl) {
        // 🟢 极其清爽的坐标转换: 1-based 直接减 1
        int targetRelativeRow = rrl.row() - 1;
        int targetCol = rrl.col() - 1;

        if (relativeRowIndex == targetRelativeRow) {
          String rawValue = cellDataMap.get(targetCol);
          if (rawValue != null && !rawValue.isBlank()) {
            sharedDiscreteData.put(field.jsonKey(), parseValue(rawValue, field));
          }
        }
      }
    }
  }

  private void buildTableColumnMapping(HorizontalTableRegionConfig tableRegion, Map<Integer, String> rowData, int relativeRowIndex, int targetIdRow, int targetNameRow) {
    for (Map.Entry<Integer, String> entry : rowData.entrySet()) {
      Integer colIndex = entry.getKey();
      String headerText = entry.getValue();
      if (headerText == null || headerText.isBlank()) {
        continue;
      }
      String cleanHeader = headerText.trim();

      for (FieldConfig field : tableRegion.fields()) {
        if (field.locator() instanceof HeaderMatchLocator hml) {
          if (relativeRowIndex == targetIdRow && hml.matchId() != null && hml.matchId().equals(cleanHeader)) {
            currentTableColumnMapping.put(colIndex, field);
            idMatchedFields.add(field);
          } else if (relativeRowIndex == targetNameRow && hml.matchName() != null && cleanHeader.matches(hml.matchName().replace("*", ".*"))) {
            if (!idMatchedFields.contains(field)) {
              currentTableColumnMapping.put(colIndex, field);
            }
          }
        }
      }
    }
  }

  private Object parseValue(String rawValue, FieldConfig config) {
    try {
      return switch (config.dataType()) {
        case STRING -> rawValue.trim();
        case NUMBER -> new BigDecimal(rawValue.trim().replace(",", ""));
        case BOOLEAN -> Boolean.parseBoolean(rawValue.trim());
        case DATE -> {
          String format = (config.format() == null || config.format().isBlank()) ? "yyyy-MM-dd" : config.format();
          yield LocalDate.parse(rawValue.trim(), DateTimeFormatter.ofPattern(format));
        }
      };
    } catch (Exception e) {
      return rawValue;
    }
  }

  @Override
  public void doAfterAllAnalysed(AnalysisContext context) {
  }
}
