package com.example.file.infrastructure.excel;

import com.example.file.domain.model.schema.ErrorFeedbackConfig;
import org.apache.fesod.sheet.write.handler.RowWriteHandler;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteTableHolder;
import org.apache.poi.ss.usermodel.*;

import java.util.Map;

public class ErrorColumnAppendHandler implements RowWriteHandler {

  private final Map<Integer, String> rowErrorMap;
  private final ErrorFeedbackConfig feedbackConfig;
  private final int headerRowIndex;

  private int errorColumnIndex = -1;
  private CellStyle errorStyle = null;

  public ErrorColumnAppendHandler(Map<Integer, String> rowErrorMap, ErrorFeedbackConfig feedbackConfig, int headerRowIndex) {
    this.rowErrorMap = rowErrorMap;
    this.feedbackConfig = feedbackConfig;
    this.headerRowIndex = headerRowIndex;
  }

  @Override
  public void afterRowDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Row row, Integer relativeRowIndex, Boolean isHead) {
    int currentRowIndex = row.getRowNum();
    Sheet sheet = writeSheetHolder.getSheet();
    Workbook workbook = writeSheetHolder.getParentWriteWorkbookHolder().getWorkbook();

    if (errorColumnIndex == -1) {
      errorColumnIndex = row.getLastCellNum() > 0 ? row.getLastCellNum() : 20;
      errorStyle = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setColor(IndexedColors.RED.getIndex());
      errorStyle.setFont(font);
    }

    if (currentRowIndex == headerRowIndex) {
      Cell cell = row.createCell(errorColumnIndex);
      cell.setCellValue(feedbackConfig.appendColumnName());

      CellStyle headerStyle = workbook.createCellStyle();
      if (row.getCell(0) != null) {
        headerStyle.cloneStyleFrom(row.getCell(0).getCellStyle());
      }
      headerStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
      cell.setCellStyle(headerStyle);
      return;
    }

    String errorMessage = rowErrorMap.get(currentRowIndex + 1);
    if (errorMessage != null) {
      Cell cell = row.createCell(errorColumnIndex);
      cell.setCellValue(errorMessage);
      cell.setCellStyle(errorStyle);
    }
  }
}
