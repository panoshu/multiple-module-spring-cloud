package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.enums.DataType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.SchemaType;
import com.example.file.domain.model.locator.RegionRelativeLocator;
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

@DisplayName("基础设施: ConfigurationDrivenReadListener 测试 (纯内存)")
class ConfigurationDrivenReadListenerTest {

  @Test
  @DisplayName("测试旧版静态坐标监听器：应成功完成严格列映射")
  void testListenerParsing() {
    // 1. 准备 Schema (配置为严格提取第 1 列/即A列的数据)
    FieldConfig idConfig = new FieldConfig("empId", FieldType.DATA_FIELD, "编号", DataType.STRING, null, false, null, new RegionRelativeLocator(1, 1), null);
    TableMeta tableMeta = new TableMeta(1, 1, 2, 1, false, "", null, null);
    ExcelSchema schema = new ExcelSchema("test", BizType.UNKNOWN, SchemaType.READ, null, null, null, null, null, null, List.of(new HorizontalTableRegionConfig("t1", tableMeta, List.of(idConfig))));

    // 2. 准备内存数据
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FesodSheet.write(out).sheet("Test").doWrite(List.of(
      List.of("编号", "其他列"), // 行 1: 表头
      List.of("001", "XXX")    // 行 2: 真实数据，"001" 在第 1 列
    ));
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    // 3. 运行监听器
    List<DataRow> collected = new ArrayList<>();
    ConfigurationDrivenReadListener listener = new ConfigurationDrivenReadListener(schema, collected::add);

    FesodSheet.read(in, listener).headRowNumber(0).sheet().doRead();

    // 4. 断言结果
    assertEquals(1, collected.size());
    assertEquals("001", collected.get(0).data().get("empId"));
  }
}
