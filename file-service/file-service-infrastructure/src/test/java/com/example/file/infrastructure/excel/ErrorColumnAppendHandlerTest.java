package com.example.file.infrastructure.excel;

import com.example.file.domain.model.schema.ErrorFeedbackConfig;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("基础设施: 错误列追加拦截器测试 (纯内存集成)")
class ErrorColumnAppendHandlerTest {

  @Test
  @DisplayName("测试错误追加：应在表头行创建列名，在对应行追加具体错误")
  void testErrorColumnAppending() throws Exception {
    // 1. 准备配置：表头在第 0 行，追加列名为 "校验结果"
    int headerRowIndex = 0;
    ErrorFeedbackConfig config = new ErrorFeedbackConfig(true, "校验结果", "_err.xlsx");

    // 2. 准备错误数据：业务第 2 行（即 POI 的第 1 行）出错
    // 注意：拦截器里使用的是 currentRowIndex + 1 来匹配 rowErrorMap
    Map<Integer, String> rowErrorMap = Map.of(2, "身份证格式错误");

    // 3. 在内存中实时生成原始数据，并挂载 Handler
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<List<String>> originalData = List.of(
      List.of("姓名", "身份证"), // 行0 (业务第1行, 表头)
      List.of("张三", "BAD_ID")  // 行1 (业务第2行, 出错行)
    );

    FesodSheet.write(out)
      .registerWriteHandler(new ErrorColumnAppendHandler(rowErrorMap, config, headerRowIndex))
      .sheet("TestSheet")
      .doWrite(originalData);

    // 4. 使用原生 POI 读取校验
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
      Sheet sheet = workbook.getSheetAt(0);

      // 原始只有 2 列 (0 和 1)，拦截器应该在第 2 列追加数据

      // 验证表头行 (行0)
      String headerExtraCell = sheet.getRow(0).getCell(2).getStringCellValue();
      assertEquals("校验结果", headerExtraCell, "表头未正确生成");

      // 验证错误数据行 (行1)
      String dataExtraCell = sheet.getRow(1).getCell(2).getStringCellValue();
      assertEquals("身份证格式错误", dataExtraCell, "错误信息未追加到正确行的末尾");
    }
  }
}
