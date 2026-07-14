package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.enums.DataType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.SchemaType;
import com.example.file.domain.model.locator.AbsoluteLocator;
import com.example.file.domain.model.locator.HeaderMatchLocator;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.TableMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("基础设施: Fesod写入引擎适配器测试")
class FesodWriteEngineAdapterTest {

  private final FesodWriteEngineAdapter adapter = new FesodWriteEngineAdapter();

  @Test
  @DisplayName("纯内存流式写入：离散区与横表区数据应成功渲染为 Excel 字节流")
  void testWriteExcel() {
    // 1. 准备离散区配置 (A1 写个静态标题，B1 填入动态数据)
    FieldConfig titleConfig = new FieldConfig("title_text", FieldType.TEXT_FIELD, "入职表单", null, null, false, null, new AbsoluteLocator("A1"), null);
    FieldConfig planConfig = new FieldConfig("planNum", FieldType.DATA_FIELD, "计划编号", DataType.STRING, null, false, null, new AbsoluteLocator("B1"), null);
    DiscreteRegionConfig discreteRegion = new DiscreteRegionConfig("d1", 1, List.of(titleConfig, planConfig));

    // 2. 准备横表区配置
    FieldConfig nameConfig = new FieldConfig("name", FieldType.DATA_FIELD, "姓名", DataType.STRING, null, true, null, new HeaderMatchLocator("NAME", "员工姓名"), null);
    TableMeta tableMeta = new TableMeta(3, 4, 5, 1, true, "DYN_", null, null); // 第2行ID表头，第3行别名表头
    HorizontalTableRegionConfig tableRegion = new HorizontalTableRegionConfig("t1", tableMeta, List.of(nameConfig));

    ExcelSchema schema = new ExcelSchema("write_test", BizType.EMPLOYEE_ONBOARDING, SchemaType.WRITE, null, null, null, null, null, null, List.of(discreteRegion, tableRegion));

    // 3. 准备业务数据
    Map<String, Object> discreteData = Map.of("planNum", "PLAN-2026-001");
    List<Map<String, Object>> tableData = List.of(
      Map.of("name", "Alice"),
      Map.of("name", "Bob", "DYN_age", "25") // 测试动态追加列
    );

    // 4. 执行写入到内存流
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    adapter.writeExcel(out, schema, discreteData, tableData);

    // 5. 验证是否成功生成了 Excel 文件字节流
    byte[] excelBytes = out.toByteArray();
    assertTrue(excelBytes.length > 0, "输出流中应该包含 Excel 文件的二进制数据");

    // （可选：如果你想深度验证，可以再用 Fesod 把 excelBytes 读出来校验）
  }
}
