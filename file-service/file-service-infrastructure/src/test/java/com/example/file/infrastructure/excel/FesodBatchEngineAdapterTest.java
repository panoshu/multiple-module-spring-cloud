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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("基础设施: Fesod全量读取适配器测试")
class FesodBatchEngineAdapterTest {

  private FesodBatchEngineAdapter batchEngineAdapter;
  private ExcelSchema schema;
  private ByteArrayInputStream excelInputStream;

  @BeforeEach
  void setUp() {
    batchEngineAdapter = new FesodBatchEngineAdapter();

    // 1. 构造简易 Schema
    FieldConfig nameConfig = new FieldConfig("name", FieldType.DATA_FIELD, "姓名", DataType.STRING, null, true, null, new HeaderMatchLocator("NAME", "姓名"), null);
    TableMeta tableMeta = new TableMeta(1, 1, 2, 1, false, "", null, null);
    HorizontalTableRegionConfig tableRegion = new HorizontalTableRegionConfig("t1", tableMeta, List.of(nameConfig));
    schema = new ExcelSchema("test", BizType.EMPLOYEE_ONBOARDING, SchemaType.READ, null, null, null, null, null, null, List.of(tableRegion));

    // 2. 造一个内存 Excel 文件
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<List<String>> excelData = List.of(
      List.of("姓名", "忽略列"), // 表头 (行0)
      List.of("张三", "忽略"),   // 数据 (行1)
      List.of("李四", "忽略")    // 数据 (行2)
    );
    FesodSheet.write(out).sheet("Test").doWrite(excelData);
    excelInputStream = new ByteArrayInputStream(out.toByteArray());
  }

  @Test
  @DisplayName("批处理读取测试：应将 Excel 一次性转换为 List<DataRow>")
  void testReadExcelBatch() {
    // 执行读取
    List<DataRow> rows = batchEngineAdapter.readExcel(excelInputStream, schema);

    // 验证
    assertEquals(2, rows.size(), "应该读取出两条业务数据");
    assertEquals("张三", rows.get(0).data().get("name"));
    assertEquals("李四", rows.get(1).data().get("name"));
  }

  @Test
  @DisplayName("不支持的写操作测试：调用废弃的 writeExcel 应抛出异常")
  void testWriteExcelThrowsException() {
    // 我们架构中明确使用了 ExcelWriteEnginePort 进行写入，全量读写引擎的 write 方法应被禁用
    assertThrows(UnsupportedOperationException.class, () -> {
      batchEngineAdapter.writeExcel(null, schema, null, null);
    });
  }
}
