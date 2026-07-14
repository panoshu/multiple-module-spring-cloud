package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.enums.DataType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.SchemaType;
import com.example.file.domain.model.locator.HeaderMatchLocator;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.TableMeta;
import com.example.file.infrastructure.excel.persistence.DynamicSchemaEventListener;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("基础设施: Fesod动态表单解析监听器测试 (纯内存集成)")
class DynamicSchemaEventListenerTest {

  private final List<DataRow> collectedRows = new ArrayList<>();
  private ExcelSchema schema;

  @BeforeEach
  void setUp() {
    // 1. 构造一个包含 ID 和 姓名 的横表测试配置
    FieldConfig idConfig = new FieldConfig("empId", FieldType.DATA_FIELD, "员工号", DataType.STRING, null, true, null, new HeaderMatchLocator("ID", "员工编号*"), null);
    FieldConfig salaryConfig = new FieldConfig("salary", FieldType.DATA_FIELD, "工资", DataType.NUMBER, null, true, null, new HeaderMatchLocator("SALARY", "基本工资*"), null);

    TableMeta tableMeta = new TableMeta(1, 2, 3, 1, false, "", null, null); // 第0行ID，第1行别名，第2行开始数据
    HorizontalTableRegionConfig tableRegion = new HorizontalTableRegionConfig("r1", tableMeta, List.of(idConfig, salaryConfig));

    schema = new ExcelSchema("test", BizType.EMPLOYEE_ONBOARDING, SchemaType.READ, null, null, null, null, null, null, List.of(tableRegion));
  }

  @Test
  @DisplayName("真实内存引擎测试：表头探测 -> 数据解析与类型转换")
  void testRealEngineParsing() {
    // 1. 在内存中实时造一个 Excel 文件
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<List<String>> excelData = List.of(
      List.of("ID", "SALARY"),               // 行0: ID 表头
      List.of("员工编号", "基本工资"),       // 行1: 中文表头
      List.of("EMP001", "15000.50"),         // 行2: 真实数据1
      List.of("EMP002", "")                  // 行3: 缺失部分数据
    );
    FesodSheet.write(out).sheet("Test").doWrite(excelData);

    // 2. 将生成的 Excel 字节流转为输入流
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    // 3. 实例化你的 Listener，并交由真实的 Fesod 引擎去跑
    DynamicSchemaEventListener listener = new DynamicSchemaEventListener(schema, collectedRows::add);
    FesodSheet.read(in, listener).headRowNumber(0).sheet().doRead();

    // 4. 验证 Listener 解析输出的结果
    assertEquals(2, collectedRows.size(), "应该成功提取出两行数据");

    DataRow resultRow1 = collectedRows.get(0);
    assertEquals(3, resultRow1.rowIndex(), "业务行号应该为底层POI行号+1");
    assertEquals("EMP001", resultRow1.data().get("empId"));
    assertEquals(new BigDecimal("15000.50"), resultRow1.data().get("salary"));

    DataRow resultRow2 = collectedRows.get(1);
    assertEquals("EMP002", resultRow2.data().get("empId"));
    assertNull(resultRow2.data().get("salary"), "空单元格不应报错，而是交由后续 Validation 服务拦截");
  }
}
