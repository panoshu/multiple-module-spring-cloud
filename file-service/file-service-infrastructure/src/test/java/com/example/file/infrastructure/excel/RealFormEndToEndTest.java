package com.example.file.infrastructure.excel;

import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.infrastructure.excel.persistence.YamlExcelSchemaRepositoryAdapter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("集成测试: 基于真实表单的 Read & Write 端到端大闭环")
class RealFormEndToEndTest {

  private final YamlExcelSchemaRepositoryAdapter repository = new YamlExcelSchemaRepositoryAdapter();
  private final FesodBatchEngineAdapter readEngine = new FesodBatchEngineAdapter();
  private final FesodWriteEngineAdapter writeEngine = new FesodWriteEngineAdapter();

  @Test
  @DisplayName("打通全链路：上传真实格式表单 -> 解析为结构化数据 -> 重新渲染为全新表单")
  void testEndToEndRealForm() throws Exception {
    // ==========================================
    // 阶段一：模拟用户上传（在内存中捏造真实 Excel 文件）
    // ==========================================
    ByteArrayOutputStream mockUploadOut = new ByteArrayOutputStream();
    List<List<String>> originalData = new ArrayList<>();
    // Row 0
    originalData.add(Arrays.asList("企业计划编号：", "0200010001", "企业计划名称：", "企业计划A"));
    // Row 1
    originalData.add(Arrays.asList("企业客户号：", "000234", "企业客户名称：", "客户A"));
    // Row 2-3 (空行与说明)
    originalData.add(Arrays.asList(""));
    originalData.add(Arrays.asList("请在填写表单之前仔细阅读填表说明，以免造成提交出错！"));
    // Row 4 (ID 表头)
    originalData.add(Arrays.asList("XH", "XM", "ZJLX", "ZJHM", "ZJYXQJZR", "GH", "XB", "CSRQ"));
    // Row 5 (分组表头)
    originalData.add(Arrays.asList("基本信息"));
    // Row 6 (中文表头)
    originalData.add(Arrays.asList("序号*", "个人姓名*", "证件类型*", "证件编号*", "证件有效期*", "工号", "性别*", "出生日期*"));
    // Row 7 (真实数据)
    originalData.add(Arrays.asList("1", "张内Aa01", "身份证", "999000198608060000", "2525/1/1", "GH01", "男", "1986-08-06"));

    FesodSheet.write(mockUploadOut).sheet("Sheet1").doWrite(originalData);
    ByteArrayInputStream mockUploadIn = new ByteArrayInputStream(mockUploadOut.toByteArray());

    // ==========================================
    // 阶段二：使用 ReadAdapter 和 read_emp_real.yaml 读取
    // ==========================================
    ExcelSchema readSchema = repository.loadSchema("read_emp_real");
    List<DataRow> parsedRows = readEngine.readExcel(mockUploadIn, readSchema);

    // 断言读取结果
    assertEquals(1, parsedRows.size(), "应该精准跳过所有表头和说明，提取出1条明细数据");
    DataRow dataRow = parsedRows.get(0);
    Map<String, Object> data = dataRow.data();

    // 验证离散区数据是否成功注入到了明细数据上下文中
    assertEquals("0200010001", data.get("planNumber"));
    assertEquals("企业计划A", data.get("planName"));

    // 验证横表区数据及类型转换
    assertEquals("张内Aa01", data.get("empName"));
    assertEquals("999000198608060000", data.get("idCard"));
    assertEquals("男", data.get("gender"));
    assertEquals(LocalDate.of(1986, 8, 6), data.get("birthDate"), "日期必须成功转换为 LocalDate 对象");


    // ==========================================
    // 阶段三：使用 WriteAdapter 和 write_emp_real.yaml 渲染
    // ==========================================
    ExcelSchema writeSchema = repository.loadSchema("write_emp_real");

    // 组装要写入的数据 (从刚读出来的 data 中剥离主体数据和明细列表)
    Map<String, Object> discreteData = Map.of(
      "planNumber", data.get("planNumber"),
      "planName", data.get("planName")
    );
    List<Map<String, Object>> tableData = List.of(data); // 只有1条员工明细

    ByteArrayOutputStream writeOut = new ByteArrayOutputStream();
    writeEngine.writeExcel(writeOut, writeSchema, discreteData, tableData);

    // ==========================================
    // 阶段四：验证渲染出的新 Excel 是否完全符合预期排版
    // ==========================================
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(writeOut.toByteArray()))) {
      Sheet sheet = workbook.getSheet("员工入职清单");
      assertNotNull(sheet, "应该成功创建名为'员工入职清单'的Sheet");

      // 🟢 使用 DataFormatter 安全提取内容（无论单元格是 STRING 还是 NUMERIC，都能安全转为文本）
      DataFormatter formatter = new DataFormatter();

      // 验证主体离散区渲染
      assertEquals("企业计划编号：", formatter.formatCellValue(sheet.getRow(0).getCell(0)));
      assertEquals("0200010001", formatter.formatCellValue(sheet.getRow(0).getCell(1)), "数据B1写入成功");
      assertEquals("企业计划名称：", formatter.formatCellValue(sheet.getRow(0).getCell(2)));
      assertEquals("企业计划A", formatter.formatCellValue(sheet.getRow(0).getCell(3)), "数据D1写入成功");

      // 验证横表双层表头渲染 (相对第1行和第3行 -> 全局索引 4 和 6)
      assertEquals("XM", formatter.formatCellValue(sheet.getRow(4).getCell(0)));
      assertEquals("个人姓名*", formatter.formatCellValue(sheet.getRow(6).getCell(0)), "必须保留配置中的星号");
      assertEquals("ZJHM", formatter.formatCellValue(sheet.getRow(4).getCell(1)));

      // 验证横表业务数据渲染 (相对第4行 -> 全局索引 7)
      assertEquals("张内Aa01", formatter.formatCellValue(sheet.getRow(7).getCell(0)));
      assertEquals("999000198608060000", formatter.formatCellValue(sheet.getRow(7).getCell(1)));
      assertEquals("男", formatter.formatCellValue(sheet.getRow(7).getCell(2)));

      // 🟢 重点：POI 将日期存为 NUMERIC，此时 DataFormatter 会根据日期格式安全将其转为字符串
      // 也可以用 sheet.getRow(7).getCell(3).getLocalDateTimeCellValue().toLocalDate().toString()
      String birthDateStr = sheet.getRow(7).getCell(3).getLocalDateTimeCellValue().toLocalDate().toString();
      assertEquals("1986-08-06", birthDateStr);
    }

    System.out.println("✅ 端到端测试大通关！解析与渲染完美对齐。");
  }
}
