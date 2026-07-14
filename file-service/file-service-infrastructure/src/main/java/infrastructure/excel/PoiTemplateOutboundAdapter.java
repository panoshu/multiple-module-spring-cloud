package infrastructure.excel;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import core.domain.model.ExportEngineType;
import core.domain.model.OutboundRule;
import core.domain.model.OutboundTemplate;
import core.domain.outbound.OutboundAdapterPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.HeaderMapping;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class PoiTemplateOutboundAdapter implements OutboundAdapterPort {

  @Override
  public boolean supports(ExportEngineType engineType) {
    return engineType == ExportEngineType.TEMPLATE;
  }

  @Override
  public void write(OutboundTemplate outboundTemplate, String jsonPayload, OutputStream outputStream) {
    OutboundRule rule = outboundTemplate.outboundRule();
    DocumentContext jsonContext = JsonPath.parse(jsonPayload);

    // 1. ★ 智能双模加载物理模板流 ★
    InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(rule.baseTemplatePath());

    try {
      // 如果 Classpath 下找不到，则尝试从本地文件系统物理路径加载 (兼容外部挂载和动态生成的测试文件)
      if (templateStream == null) {
        File localFile = new File(rule.baseTemplatePath());
        if (localFile.exists()) {
          templateStream = new FileInputStream(localFile);
        } else {
          throw new RuntimeException("找不到底座物理模板 (Classpath 与本地文件系统均未命中): " + rule.baseTemplatePath());
        }
      }

      // 2. 加载进 POI 内存
      try (Workbook workbook = WorkbookFactory.create(templateStream)) {
        Sheet sheet = workbook.getSheetAt(0);

        // 3. 精准填空：头信息
        if (rule.headerZone() != null && rule.headerZone().fields() != null) {
          for (HeaderMapping mapping : rule.headerZone().fields()) {
            try {
              Object value = jsonContext.read(mapping.jsonPath());
              int r = CoordinateUtils.getRowIndex(mapping.cell());
              int c = CoordinateUtils.getColIndex(mapping.cell());
              writeCellValue(sheet, r, c, value);
            } catch (Exception e) {
              // 忽略缺失值
            }
          }
        }

        // 4. 精准填空：明细信息
        if (rule.detailZone() != null && rule.detailZone().fields() != null) {
          int currentRow = rule.detailZone().startRow() - 1;
          String arrayPath = extractArrayPath(rule.detailZone().fields().get(0).jsonPath());
          Integer listSize = jsonContext.read(arrayPath + ".length()");

          if (listSize != null && listSize > 0) {
            for (int i = 0; i < listSize; i++) {
              for (DetailMapping mapping : rule.detailZone().fields()) {
                String exactPath = mapping.jsonPath().replace("[*]", "[" + i + "]");
                try {
                  Object value = jsonContext.read(exactPath);
                  int colIndex = CoordinateUtils.colNameToIndex(mapping.col());
                  writeCellValue(sheet, currentRow, colIndex, value);
                } catch (Exception e) {
                  // 忽略缺失值
                }
              }
              currentRow++;
            }
          }
        }

        // 5. 保存文件
        workbook.write(outputStream);
      }
    } catch (Exception e) {
      throw new RuntimeException("模板渲染导出失败", e);
    } finally {
      // 安全关闭流
      if (templateStream != null) {
        try {
          templateStream.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private void writeCellValue(Sheet sheet, int rowIndex, int colIndex, Object value) {
    if (value == null) {
      return;
    }
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      row = sheet.createRow(rowIndex);
    }

    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      cell = row.createCell(colIndex);
    }

    if (value instanceof Number n) {
      cell.setCellValue(n.doubleValue());
    } else if (value instanceof Boolean b) {
      cell.setCellValue(b);
    } else {
      cell.setCellValue(value.toString());
    }
  }

  private String extractArrayPath(String fullPath) {
    int index = fullPath.indexOf("[*]");
    return index > 0 ? fullPath.substring(0, index) : fullPath;
  }
}
