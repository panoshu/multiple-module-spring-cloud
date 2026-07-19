package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FesodExcelExporterTest {

  @TempDir
  Path tempDir;

  @Test
  void export_填充简单变量和列表变量() throws Exception {
    // 1. 准备模板（用 fesod 写入占位符字符串）
    Path templatePath = tempDir.resolve("template.xlsx");
    List<List<Object>> templateRows = List.of(
        Arrays.asList("姓名：{name}", null, "年龄：{age}"),
        List.of("序号", "产品", "数量"),
        List.of("{items.seq}", "{items.product}", "{items.qty}"));
    FesodSheet.write(templatePath.toString()).sheet().doWrite(templateRows);

    // 2. 构造 SplitUnit（模拟 TaskSplitter 输出）
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "张三");
    data.put("age", "30");
    data.put("items", List.of(
        Map.of("seq", "1", "product", "苹果", "qty", "10"),
        Map.of("seq", "2", "product", "香蕉", "qty", "20")));
    SplitUnit unit = new SplitUnit("default", data);

    // 3. 调用 export
    Path outputPath = tempDir.resolve("output.xlsx");
    ExcelExporter exporter = new FesodExcelExporter();
    try (InputStream tpl = new FileInputStream(templatePath.toFile());
         OutputStream out = new FileOutputStream(outputPath.toFile())) {
      exporter.export(unit, tpl, out);
    }

    // 4. 验证输出文件存在且非空
    assertThat(Files.exists(outputPath)).isTrue();
    assertThat(Files.size(outputPath)).isGreaterThan(0);

    // 5. 重新读取输出文件，验证占位符被替换
    List<Map<Integer, String>> rows = FesodSheet.read(outputPath.toString())
        .sheet()
        .headRowNumber(0)
        .doReadSync();
    // 第 0 行：姓名：张三 | null | 年龄：30
    assertThat(rows.get(0).get(0)).isEqualTo("姓名：张三");
    assertThat(rows.get(0).get(2)).isEqualTo("年龄：30");
    // 第 2 行（列表第 1 项）：1 | 苹果 | 10
    assertThat(rows.get(2).get(0)).isEqualTo("1");
    assertThat(rows.get(2).get(1)).isEqualTo("苹果");
    assertThat(rows.get(2).get(2)).isEqualTo("10");
    // 第 3 行（列表第 2 项）：2 | 香蕉 | 20
    assertThat(rows.get(3).get(0)).isEqualTo("2");
    assertThat(rows.get(3).get(1)).isEqualTo("香蕉");
    assertThat(rows.get(3).get(2)).isEqualTo("20");
  }

  @Test
  void export_空列表时不报错() throws Exception {
    Path templatePath = tempDir.resolve("template.xlsx");
    List<List<Object>> templateRows = List.of(
        List.of("姓名：{name}"),
        List.of("{items.seq}"));
    FesodSheet.write(templatePath.toString()).sheet().doWrite(templateRows);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "李四");
    data.put("items", List.of());
    SplitUnit unit = new SplitUnit("default", data);

    Path outputPath = tempDir.resolve("output.xlsx");
    ExcelExporter exporter = new FesodExcelExporter();
    try (InputStream tpl = new FileInputStream(templatePath.toFile());
         OutputStream out = new FileOutputStream(outputPath.toFile())) {
      exporter.export(unit, tpl, out);
    }

    assertThat(Files.exists(outputPath)).isTrue();
    List<Map<Integer, String>> rows = FesodSheet.read(outputPath.toString())
        .sheet()
        .headRowNumber(0)
        .doReadSync();
    assertThat(rows.get(0).get(0)).isEqualTo("姓名：李四");
    // 空列表时 FesodExcelExporter 不调用 writer.fill(FillWrapper)（if (!list.isEmpty()) 守卫），
    // 但 fesod 在 fill(simpleVars, writeSheet) 扫描模板时会清理含 {items.seq} 占位符的行
    // （因为 simpleVars 中没有 items 键），所以输出只剩 1 行。这是 fesod 的预期行为。
    assertThat(rows).hasSize(1);
  }
}
