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
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("基础设施: Fesod 流式读取引擎适配器测试")
class FesodStreamingEngineAdapterTest {

  private final FesodStreamingEngineAdapter adapter = new FesodStreamingEngineAdapter();

  @Test
  @DisplayName("测试流式适配器：应成功接管 InputStream 并将 DataRow 回调至 Consumer")
  void testReadExcelStream() {
    // 1. 准备最小可用 Schema
    FieldConfig nameConfig = new FieldConfig("name", FieldType.DATA_FIELD, "姓名", DataType.STRING, null, true, null, new HeaderMatchLocator("NAME", "姓名"), null);
    TableMeta tableMeta = new TableMeta(1, 1, 2, 1, false, "", null, null);
    HorizontalTableRegionConfig tableRegion = new HorizontalTableRegionConfig("t1", tableMeta, List.of(nameConfig));
    ExcelSchema schema = new ExcelSchema("test", BizType.EMPLOYEE_ONBOARDING, SchemaType.READ, null, null, null, null, null, null, List.of(tableRegion));

    // 2. 准备内存 Excel
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FesodSheet.write(out).sheet("Test").doWrite(List.of(
      List.of("姓名", "备注"),
      List.of("Jack", "Good")
    ));
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    // 3. 执行流式读取，使用 Consumer 收集数据
    List<DataRow> collectedRows = new ArrayList<>();
    adapter.readExcelStream(in, schema, collectedRows::add);

    // 4. 验证是否成功流出数据
    assertEquals(1, collectedRows.size());
    assertEquals("Jack", collectedRows.get(0).data().get("name"));
  }
}
