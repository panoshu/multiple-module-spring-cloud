// 文件: src/main/java/infrastructure/excel/PoiErrorExcelExporter.java
package infrastructure.excel;

import core.domain.model.ErrorRecord;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 POI 克隆模式的完美错题本生成器
 */
public class PoiErrorExcelExporter {

  public void exportErrorExcel(InputStream originalStream, List<ErrorRecord> errors, OutputStream errorOutputStream) {

    // 1. 将错误按行号合并
    Map<Integer, String> errorMap = errors.stream()
      .filter(e -> e.rowIndex() > 0)
      .collect(Collectors.groupingBy(
        ErrorRecord::rowIndex,
        Collectors.mapping(ErrorRecord::message, Collectors.joining(" | "))
      ));

    if (errorMap.isEmpty()) {
      return;
    }

    // 2. 将原始文件完全加载入内存 (完美保留 100% 格式和无关文本)
    try (Workbook workbook = WorkbookFactory.create(originalStream)) {
      Sheet sheet = workbook.getSheetAt(0);

      // 3. 动态探测表头最大列宽 (假设我们通过第 5 行来探测)
      int errorColIndex = 0;
      Row probeRow = sheet.getRow(4); // 0-based, 第 5 行
      if (probeRow != null) {
        errorColIndex = probeRow.getLastCellNum(); // 获取最后一列的索引
      } else {
        errorColIndex = 20; // 兜底放在 U 列
      }

      // 4. 写入多行表头 (与原表单齐平)
      writeCell(sheet, 4, errorColIndex, "ERROR_MSG");      // 第 5 行: 字段ID
      writeCell(sheet, 5, errorColIndex, "系统校验");         // 第 6 行: 分类
      writeCell(sheet, 6, errorColIndex, "错误信息提示");     // 第 7 行: 中文名

      // 给错误提示表头加点红色预警样式
      CellStyle errorHeaderStyle = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setColor(IndexedColors.RED.getIndex());
      font.setBold(true);
      errorHeaderStyle.setFont(font);
      sheet.getRow(6).getCell(errorColIndex).setCellStyle(errorHeaderStyle);

      // 5. 遍历并写入具体的错误信息
      for (Map.Entry<Integer, String> entry : errorMap.entrySet()) {
        int rowIndexZeroBased = entry.getKey() - 1;
        writeCell(sheet, rowIndexZeroBased, errorColIndex, entry.getValue());
      }

      // 6. 将加工后的完整文件流式写出
      workbook.write(errorOutputStream);

    } catch (Exception e) {
      throw new RuntimeException("生成原样错题本失败", e);
    }
  }

  private void writeCell(Sheet sheet, int rowIndex, int colIndex, String value) {
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      row = sheet.createRow(rowIndex);
    }
    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      cell = row.createCell(colIndex);
    }
    cell.setCellValue(value);
  }
}
