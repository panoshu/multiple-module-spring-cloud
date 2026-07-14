package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.Direction;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.locator.AbsoluteLocator;
import com.example.file.domain.model.locator.AnchorRelativeLocator;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.Style;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.List;
import java.util.Map;

/**
 * DiscreteRegionWriteHandler
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/27 11:30
 */
public class DiscreteRegionWriteHandler implements SheetWriteHandler {

  private final List<DiscreteRegionConfig> discreteRegions;
  private final Map<String, Object> discreteData;

  public DiscreteRegionWriteHandler(List<DiscreteRegionConfig> discreteRegions, Map<String, Object> discreteData) {
    this.discreteRegions = discreteRegions;
    this.discreteData = discreteData;
  }

  @Override
  public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
    Sheet sheet = writeSheetHolder.getSheet();

    for (DiscreteRegionConfig region : discreteRegions) {
      for (FieldConfig field : region.fields()) {

        // 1. 确定要写入的内容
        String contentToWrite = "";
        if (field.fieldType() == FieldType.TEXT_FIELD) {
          contentToWrite = field.label(); // 静态文本直接取 label
        } else if (field.fieldType() == FieldType.DATA_FIELD) {
          Object val = discreteData.get(field.jsonKey()); // 动态数据从传入的 Map 中取
          contentToWrite = val != null ? String.valueOf(val) : "";
        }

        if (contentToWrite.isEmpty()) {
          continue;
        }

        // 2. 利用 JDK 25 switch 模式匹配，确定坐标并写入
        switch (field.locator()) {
          case AbsoluteLocator al -> {
            int[] coords = resolveCellCoordinates(al.cell());
            writeToCell(sheet, coords[0], coords[1], contentToWrite, field.style());
          }
          case AnchorRelativeLocator arl -> {
            // 写入模式下，锚点相对定位意味着：我们需要先找到锚点文本所在的坐标，然后再偏移
            int[] anchorCoords = findTextInSheet(sheet, arl.anchorText());
            if (anchorCoords != null) {
              int targetCol = anchorCoords[1] + (arl.direction() == Direction.RIGHT ? arl.offset() : 0);
              int targetRow = anchorCoords[0] + (arl.direction() == Direction.DOWN ? arl.offset() : 0);
              writeToCell(sheet, targetRow, targetCol, contentToWrite, field.style());
            }
          }
          default -> {
          }
        }
      }
    }
  }

  // 将内容写入到指定的行和列
  private void writeToCell(Sheet sheet, int rowIndex, int colIndex, String content, Style styleConfig) {
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      row = sheet.createRow(rowIndex);
    }

    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      cell = row.createCell(colIndex);
    }

    cell.setCellValue(content);

    // 此处可结合 field.style() (如 fontBold, fontColor) 利用 POI 动态创建 CellStyle
    // 省略样式组装代码，保持核心逻辑清晰...
  }

  // 坐标转换器 "A4" -> [3, 0]
  private int[] resolveCellCoordinates(String cellRef) {
    int col = cellRef.charAt(0) - 'A';
    int row = Integer.parseInt(cellRef.substring(1)) - 1;
    return new int[]{row, col};
  }

  // 在 Sheet 中暴力检索锚点文本的坐标 (在实际场景中，为提升性能，可构建全量索引)
  private int[] findTextInSheet(Sheet sheet, String targetText) {
    for (Row row : sheet) {
      for (Cell cell : row) {
        if (cell.getCellType() == CellType.STRING && targetText.equals(cell.getStringCellValue())) {
          return new int[]{row.getRowNum(), cell.getColumnIndex()};
        }
      }
    }
    return null;
  }
}
