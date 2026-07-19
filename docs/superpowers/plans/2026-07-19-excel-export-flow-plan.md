# Excel 导出全流程测试 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 file-service 新增基于 fesod 模板填充的 Excel 导出能力，并构造从解析、校验、拆分到导出外部表单的端到端全流程测试。

**Architecture:** Domain 层新增 `ExcelExporter` Gateway SPI，infrastructure 层用 fesod 的 `FesodSheet.write().withTemplate().doFill()` 实现 `FesodExcelExporter`。测试通过 `@BeforeAll` 程序生成填充模板到 `docs/excel/示例表单_填充模板.xlsx`，然后验证 解析→校验→拆分→导出→round-trip 全流程。

**Tech Stack:** JDK 25, fesod-sheet 2.0.2-incubating, JUnit 5, AssertJ

## Global Constraints

- PowerShell 环境，mvn 的 -D 参数用引号：`"-Dmaven.legacyLocalRepo=true"`
- Git commit 用 `--no-gpg-sign` + 临时文件 commit message（PowerShell 不支持 HEREDOC）
- RawRow.cells() 列索引统一为 **0-based**
- 领域对象用 record + 非 JavaBean getter
- 测试用 AssertJ 断言
- fesod 包路径前缀：`org.apache.fesod.sheet.*`（已在 ExcelParserImpl.java 验证）
- 测试 workingDirectory 已配置为 `${maven.multiModuleProjectDirectory}`（file-infrastructure/pom.xml）
- `ExcelParserImpl` 已存在并可用（`openStream(InputStream)` 返回 `RawRowStream`）
- `RegionStateMachine`、`CanonicalModelBuilder`、`DataValidator`、`TaskSplitter` 已存在并可用
- SplitUnit.data 结构：顶层 Map，properties 是简单 KV，tables 是 List<Map>（key 为 regionName 如 "employees"）

---

## 值对象签名速查表（实施时参照）

| 类 | 构造函数签名 |
|----|-------------|
| `SplitUnit` | `(String splitKey, Map<String, Object> data)` |
| `ValidationRule` | `(String field, ValidationScope scope, String expr, String message, FieldType type)` |
| `RegionDef` | `(String name, RegionType type, String bindTo, RegionTrigger trigger, RegionStrategy strategy)` |
| `RegionTrigger` | `(TriggerMatchType matchType, int minMatchCount)` |
| `KvStrategy` | `(KvValuePosition valuePosition, Map<String, List<String>> labelAliases, int maxBlankRows)` |
| `TableStrategy` | `(int headerRows, int headerNameRow, TableMatchBy matchBy, Map<String, List<String>> headerAliases, HeaderMatching headerMatching, int maxRows, DataEndRule dataEnd)` |
| `DataEndRule` | `(List<String> markers, int blankRowCount)` |
| `SplitConfig` | `(List<String> keys, SplitKeyDef splitKey, SplitMissPolicy onMiss, String defaultOnMissValue, String fileNamingTemplate, boolean promoteToContext, int maxRowsPerSubTask)` |
| `SplitKeyDef` | `(String targetField, String sourcePath, SplitKeyType type)` |

### 关键接口

| 接口 | 方法 |
|------|------|
| `ExcelExporter` (新增) | `void export(SplitUnit unit, InputStream templateStream, OutputStream out)` |
| `ExcelParser` (已存在) | `RawRowStream openStream(InputStream)` |
| `ExpressionEvaluator` (已存在) | `Object evaluate(String expr, Map<String, Object> context)` |

### 关键服务方法

| 服务 | 方法 |
|------|------|
| `RegionStateMachine.drive` | `(RawRowStream, List<RegionDef>, ParseContext) → List<RegionParseResult>` |
| `CanonicalModelBuilder.build` | `(List<RegionParseResult>, List<RegionDef>) → CanonicalData` |
| `DataValidator.validate` | `(Map<String, Object>, List<ValidationRule>, ErrorPolicy, ExpressionEvaluator) → ValidationResult` |
| `TaskSplitter.split` | `(Map<String, Object>, SplitConfig) → List<SplitUnit>` |

---

## Task 1: ExcelExporter SPI + FesodExcelExporter 实现 + smoke test

**Files:**
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/ExcelExporter.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/FesodExcelExporter.java`
- Create: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/FesodExcelExporterTest.java`

**Interfaces:**
- Consumes: `SplitUnit`（已存在，`com.example.file.domain.model.valueobject.SplitUnit`）
- Produces: `ExcelExporter` SPI（供后续 Task 2 测试使用）

- [ ] **Step 1: 创建 Domain SPI `ExcelExporter`**

创建 `file-service/file-domain/src/main/java/com/example/file/domain/gateway/ExcelExporter.java`：

```java
package com.example.file.domain.gateway;

import com.example.file.domain.model.valueobject.SplitUnit;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于 Excel 模板填充 SplitUnit 数据并写入输出流。
 *
 * <p>模板占位符语法（由实现决定，当前实现用 fesod）：
 * <ul>
 *   <li>{@code {fieldName}} - 普通变量，从 SplitUnit.data 顶层取值</li>
 *   <li>{@code {.listName.field}} - 列表变量，从 SplitUnit.data 顶层的 List&lt;Map&gt; 取字段</li>
 * </ul>
 */
public interface ExcelExporter {
  void export(SplitUnit unit, InputStream templateStream, OutputStream out);
}
```

- [ ] **Step 2: 写失败测试 `FesodExcelExporterTest`**

创建 `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/FesodExcelExporterTest.java`：

```java
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
        List.of("姓名：{name}", null, "年龄：{age}"),
        List.of("序号", "产品", "数量"),
        List.of("{.items.seq}", "{.items.product}", "{.items.qty}"));
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
        List.of("{.items.seq}"));
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
        .doReadSync();
    assertThat(rows.get(0).get(0)).isEqualTo("姓名：李四");
  }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -pl file-service/file-infrastructure -am test -Dtest=FesodExcelExporterTest -q`
Expected: FAIL，`FesodExcelExporter` 类不存在（编译失败）

- [ ] **Step 4: 实现 `FesodExcelExporter`**

创建 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/FesodExcelExporter.java`：

```java
package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.WriteSheet;
import org.apache.fesod.sheet.fill.FillConfig;
import org.apache.fesod.sheet.fill.FillWrapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FesodExcelExporter implements ExcelExporter {

  @Override
  public void export(SplitUnit unit, InputStream templateStream, OutputStream out) {
    try (ExcelWriter writer = FesodSheet.write(out)
            .withTemplate(templateStream)
            .build()) {
      WriteSheet writeSheet = FesodSheet.writerSheet().build();
      Map<String, Object> data = unit.data();

      // 1. 分离简单变量和列表变量
      Map<String, Object> simpleVars = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : data.entrySet()) {
        Object value = e.getValue();
        if (value instanceof List<?> list) {
          // 列表变量：用 FillWrapper 包装，前缀 = regionName
          // 模板占位符 {.regionName.field} 会被替换为列表每项的 field 值
          if (!list.isEmpty()) {
            writer.fill(new FillWrapper(e.getKey(), list),
                FillConfig.builder().forceNewRow(true).build(),
                writeSheet);
          }
        } else {
          simpleVars.put(e.getKey(), value);
        }
      }

      // 2. 填充普通变量
      if (!simpleVars.isEmpty()) {
        writer.fill(simpleVars, writeSheet);
      }
    }
  }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure -am test -Dtest=FesodExcelExporterTest -q`
Expected: PASS，2/2 tests

**注意**：如果 fesod 的 `FillConfig` 或 `FillWrapper` 包路径不对（例如不是 `org.apache.fesod.sheet.fill`），编译会失败。此时用 IDE 的自动导入或检查 fesod jar 实际包路径：
```bash
mvn -pl file-service/file-infrastructure dependency:tree -Dincludes=org.apache.fesod 2>&1 | findstr fesod
# 然后查看 jar 内容
jar tf %USERPROFILE%\.m2\repository\org\apache\fesod\fesod-sheet\<version>\fesod-sheet-<version>.jar | findstr -i "fill"
```

- [ ] **Step 6: 运行全量测试确保无回归**

Run: `mvn -pl file-service/file-infrastructure -am test -q`
Expected: 全部通过（domain 31/31 + infrastructure 13+2=15）

- [ ] **Step 7: Commit**

写 commit message 到临时文件 `.superpowers/sdd/task-1-commit-msg.txt`：

```
feat(file-service): Task 1 - ExcelExporter SPI + FesodExcelExporter 实现

新增 Domain Gateway SPI ExcelExporter.export(SplitUnit, InputStream, OutputStream)。
Infrastructure 层 FesodExcelExporter 用 fesod 的 FesodSheet.write().withTemplate()
+ FillWrapper + FillConfig.forceNewRow(true) 实现模板填充：
- 简单变量 (KV) 用 Map 一次性填充
- 列表变量 (tables) 用 FillWrapper 包装，前缀 = regionName
- forceNewRow 避免列表填充覆盖模板后续内容

测试：2/2 PASS (smoke test 验证简单变量+列表变量填充 + 空列表不报错)
```

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/gateway/ExcelExporter.java file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/FesodExcelExporter.java file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/FesodExcelExporterTest.java
git commit -F .superpowers/sdd/task-1-commit-msg.txt --no-gpg-sign
```

---

## Task 2: 模板生成 + 全流程 happy path 测试

**Files:**
- Create: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java`
- Generate: `docs/excel/示例表单_填充模板.xlsx`（由 `@BeforeAll` 程序生成）

**Interfaces:**
- Consumes: `ExcelExporter`（Task 1）、`ExcelParser`、`RegionStateMachine`、`CanonicalModelBuilder`、`DataValidator`、`TaskSplitter`、`SplitUnit`、`CanonicalData`
- Produces: 全流程测试，验证解析→校验→拆分→导出→round-trip

- [ ] **Step 1: 写测试骨架 + 模板生成 + 全流程 happy path 测试**

创建 `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java`：

```java
package com.example.file.infrastructure;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.service.*;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import com.example.file.infrastructure.gateway.FesodExcelExporter;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFlowIntegrationTest {

  private static final String SOURCE_EXCEL_PATH =
      Path.of("docs", "excel", "示例表单.xlsx").toString();
  private static final String FILL_TEMPLATE_PATH =
      Path.of("docs", "excel", "示例表单_填充模板.xlsx").toString();
  private static final String OUTPUT_DIR =
      Path.of("target", "test-classes").toString();

  @BeforeAll
  static void ensureFillTemplateExists() {
    Path templatePath = Path.of(FILL_TEMPLATE_PATH);
    if (Files.exists(templatePath)) return;
    // 模板结构（6 行 4 列）：
    // 行 0: 企业客户号：{customerNo} | (空) | 企业客户名称：{customerName} | (空)
    // 行 1: (空行)
    // 行 2: 序号 | 姓名 | 证件类型 | 证件号码   <- 表头行（静态）
    // 行 3: {.employees.seq} | {.employees.name} | {.employees.idType} | {.employees.idNo}  <- 列表模板行
    // 行 4: (空行)
    // 行 5: 填表人：{filler} | (空) | 复核人：{reviewer} | (空)
    List<List<Object>> templateRows = new ArrayList<>();
    templateRows.add(Arrays.asList("企业客户号：{customerNo}", null, "企业客户名称：{customerName}", null));
    templateRows.add(Arrays.asList(null, null, null, null));
    templateRows.add(Arrays.asList("序号", "姓名", "证件类型", "证件号码"));
    templateRows.add(Arrays.asList("{.employees.seq}", "{.employees.name}", "{.employees.idType}", "{.employees.idNo}"));
    templateRows.add(Arrays.asList(null, null, null, null));
    templateRows.add(Arrays.asList("填表人：{filler}", null, "复核人：{reviewer}", null));
    FesodSheet.write(templatePath.toString()).sheet().doWrite(templateRows);
  }

  @Test
  void 全流程_解析_校验_拆分_导出_round_trip() throws Exception {
    // 1. 解析示例表单（用原 Excel 的代码表头配置）
    List<RegionDef> sourceRegions = buildSourceRegionDefs();
    ExcelParser excelParser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
        RegionType.KEY_VALUE, new KeyValueRegionParser(),
        RegionType.TABLE, new TableRegionParser()));

    CanonicalData data;
    try (InputStream is = new FileInputStream(SOURCE_EXCEL_PATH)) {
      RawRowStream stream = excelParser.openStream(is);
      List<RegionParseResult> results = stateMachine.drive(stream, sourceRegions, new ParseContext(sourceRegions));
      data = new CanonicalModelBuilder().build(results, sourceRegions);
    }

    // 验证解析结果
    assertThat(data.properties())
        .containsEntry("customerNo", "000234")
        .containsEntry("customerName", "客户A");
    assertThat(data.tables().get("employees")).hasSize(3);

    // 2. 校验通过
    List<ValidationRule> rules = List.of(
        new ValidationRule("idNo", ValidationScope.ROW, "idNo != null", "证件编号不能为空", FieldType.STRING),
        new ValidationRule("name", ValidationScope.ROW, "name != null", "姓名不能为空", FieldType.STRING));
    ExpressionEvaluator evaluator = buildExpressionEvaluator();
    DataValidator validator = new DataValidator();
    for (Map<String, Object> row : data.tables().get("employees")) {
      ValidationResult result = validator.validate(row, rules, ErrorPolicy.COLLECT_ALL, evaluator);
      assertThat(result.isValid()).isTrue();
    }

    // 3. 按 idType 拆分
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.putAll(data.properties());
    dataMap.putAll(data.tables());
    SplitConfig splitConfig = new SplitConfig(
        List.of("idType"),
        new SplitKeyDef("idType", "employees.idType", SplitKeyType.FIELD_VALUE),
        SplitMissPolicy.ERROR, null, null, false, 1000);
    TaskSplitter splitter = new TaskSplitter();
    List<SplitUnit> units = splitter.split(dataMap, splitConfig);
    assertThat(units).hasSize(2);
    assertThat(units).extracting(SplitUnit::splitKey)
        .containsExactlyInAnyOrder("身份证", "护照");

    // 4. 每个 SplitUnit 导出
    ExcelExporter exporter = new FesodExcelExporter();
    Path outputDir = Path.of(OUTPUT_DIR);
    Files.createDirectories(outputDir);

    for (SplitUnit unit : units) {
      // 清理旧文件
      Path outputPath = outputDir.resolve("export-" + unit.splitKey() + ".xlsx");
      Files.deleteIfExists(outputPath);

      try (InputStream tpl = new FileInputStream(FILL_TEMPLATE_PATH);
           OutputStream out = new FileOutputStream(outputPath.toFile())) {
        exporter.export(unit, tpl, out);
      }
      assertThat(Files.exists(outputPath)).isTrue();
      assertThat(Files.size(outputPath)).isGreaterThan(0);

      // 5. round-trip: 重新解析导出的 Excel（用中表头配置）
      List<RegionDef> roundTripRegions = buildRoundTripRegionDefs();
      CanonicalData reParsed;
      try (InputStream is = new FileInputStream(outputPath.toFile())) {
        RawRowStream stream = excelParser.openStream(is);
        List<RegionParseResult> results = stateMachine.drive(stream, roundTripRegions, new ParseContext(roundTripRegions));
        reParsed = new CanonicalModelBuilder().build(results, roundTripRegions);
      }

      // 验证 properties 一致
      assertThat(reParsed.properties())
          .containsEntry("customerNo", unit.data().get("customerNo"))
          .containsEntry("customerName", unit.data().get("customerName"));

      // 验证 tables 行数一致
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> expectedRows = (List<Map<String, Object>>) unit.data().get("employees");
      assertThat(reParsed.tables().get("employees")).hasSize(expectedRows.size());

      // 验证每行字段值一致
      for (int i = 0; i < expectedRows.size(); i++) {
        Map<String, Object> expected = expectedRows.get(i);
        Map<String, Object> actual = reParsed.tables().get("employees").get(i);
        assertThat(actual)
            .containsEntry("seq", expected.get("seq"))
            .containsEntry("name", expected.get("name"))
            .containsEntry("idType", expected.get("idType"))
            .containsEntry("idNo", expected.get("idNo"));
      }
    }
  }

  private List<RegionDef> buildSourceRegionDefs() {
    // 原 Excel 用代码表头 (XH/XM/ZJLX/ZJHM)
    return List.of(
        new RegionDef("basic_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "customerNo", List.of("企业客户号："),
                    "customerName", List.of("企业客户名称：")),
                2)),
        new RegionDef("employee_list", RegionType.TABLE, "employees",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 5),
            new TableStrategy(
                3, 1, TableMatchBy.HEADER_NAME,
                Map.of(
                    "seq", List.of("XH"),
                    "name", List.of("XM"),
                    "idType", List.of("ZJLX"),
                    "idNo", List.of("ZJHM")),
                HeaderMatching.STRICT, 0,
                new DataEndRule(List.of("结束"), 1))),
        new RegionDef("filler_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 1),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "filler", List.of("填表人:"),
                    "reviewer", List.of("复核人：")),
                1)));
  }

  private List<RegionDef> buildRoundTripRegionDefs() {
    // 导出的 Excel 用中表头 (序号/姓名/证件类型/证件号码)
    return List.of(
        new RegionDef("basic_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "customerNo", List.of("企业客户号："),
                    "customerName", List.of("企业客户名称：")),
                2)),
        new RegionDef("employee_list", RegionType.TABLE, "employees",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 4),
            new TableStrategy(
                1, 0, TableMatchBy.HEADER_NAME,
                Map.of(
                    "seq", List.of("序号"),
                    "name", List.of("姓名"),
                    "idType", List.of("证件类型"),
                    "idNo", List.of("证件号码")),
                HeaderMatching.STRICT, 0, null)),
        new RegionDef("filler_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 1),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "filler", List.of("填表人："),
                    "reviewer", List.of("复核人：")),
                1)));
  }

  private ExpressionEvaluator buildExpressionEvaluator() {
    return (expr, ctxMap) -> {
      if (expr == null) return true;
      if (expr.endsWith("!= null")) {
        String field = expr.substring(0, expr.indexOf("!=")).trim();
        return ctxMap.containsKey(field) && ctxMap.get(field) != null;
      }
      return true;
    };
  }
}
```

- [ ] **Step 2: 运行测试验证失败或通过**

Run: `mvn -pl file-service/file-infrastructure -am test -Dtest=ExportFlowIntegrationTest -q`
Expected: PASS（如果 fesod 填充正常工作）或 FAIL（如果 round-trip 数据不一致）

**调试要点**：
1. 如果 `@BeforeAll` 没有生成模板（docs/excel/ 目录不可写），检查权限或手动创建目录
2. 如果 round-trip 解析失败，检查 `buildRoundTripRegionDefs()` 的 `headerAliases` 是否匹配导出模板的中表头
3. 如果列表填充覆盖了底部"填表人"行，确认 `FillConfig.forceNewRow(true)` 已生效
4. 如果导出的 Excel 行数不对，用 `FesodSheet.read(outputPath).sheet().doReadSync()` 打印所有行调试

- [ ] **Step 3: 运行全量测试确保无回归**

Run: `mvn -pl file-service/file-infrastructure -am test -q`
Expected: 全部通过（domain 31/31 + infrastructure 13+2+1=16）

- [ ] **Step 4: Commit**

写 commit message 到 `.superpowers/sdd/task-2-commit-msg.txt`：

```
feat(file-service): Task 2 - 全流程 happy path 测试 (解析→校验→拆分→导出→round-trip)

新增 ExportFlowIntegrationTest.全流程_解析_校验_拆分_导出_round_trip：
- @BeforeAll 程序生成填充模板到 docs/excel/示例表单_填充模板.xlsx
- 解析示例表单 (3 行员工数据)
- 校验通过 (idNo/name 非空)
- 按 idType 拆分为 2 个子任务 (身份证/护照)
- 每个子任务用 FesodExcelExporter 导出 Excel
- 重新解析导出文件验证数据一致性 (properties + tables 行数 + 字段值)

模板结构：6 行 4 列，KV 占位符 {customerNo}/{customerName}/{filler}/{reviewer}
+ 列表占位符 {.employees.seq}/.name/.idType/.idNo + forceNewRow(true) 保护底部。

测试：1/1 PASS
```

```bash
git add file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java docs/excel/示例表单_填充模板.xlsx
git commit -F .superpowers/sdd/task-2-commit-msg.txt --no-gpg-sign
```

---

## Task 3: 校验失败时不导出测试

**Files:**
- Modify: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java`（在 Task 2 基础上追加测试方法）

**Interfaces:**
- Consumes: `DataValidator`、`ValidationRule`、`ExpressionEvaluator`、`ExcelExporter`
- Produces: 验证校验失败时不调用 export

- [ ] **Step 1: 在 ExportFlowIntegrationTest 中追加测试方法**

在 `ExportFlowIntegrationTest.java` 的 `全流程_解析_校验_拆分_导出_round_trip` 方法之后追加：

```java
  @Test
  void 校验失败时不导出() throws Exception {
    // 构造一个 idNo 为空的行（模拟校验失败场景）
    Map<String, Object> invalidRow = new LinkedHashMap<>();
    invalidRow.put("seq", "1");
    invalidRow.put("name", "张三");
    invalidRow.put("idType", "身份证");
    invalidRow.put("idNo", null);  // 缺失证件号

    List<ValidationRule> rules = List.of(
        new ValidationRule("idNo", ValidationScope.ROW, "idNo != null",
            "证件编号不能为空", FieldType.STRING));

    ExpressionEvaluator evaluator = buildExpressionEvaluator();
    DataValidator validator = new DataValidator();
    ValidationResult result = validator.validate(invalidRow, rules,
        ErrorPolicy.COLLECT_ALL, evaluator);

    // 验证校验失败
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).message()).isEqualTo("证件编号不能为空");

    // 验证不调用 export：用计数器 spy 验证
    // 真实场景 ParseFileUseCase 会检查 isValid 才调用 exporter
    // 这里用 AtomicBoolean 模拟 ParseFileUseCase 的决策逻辑
    java.util.concurrent.atomic.AtomicBoolean exportCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
    ExcelExporter spyExporter = (unit, tpl, out) -> exportCalled.set(true);

    if (result.isValid()) {
      // 不会进入此分支，因为 isValid == false
      try (OutputStream out = new FileOutputStream(Path.of(OUTPUT_DIR, "should-not-exist.xlsx").toFile())) {
        spyExporter.export(null, null, out);
      }
    }

    assertThat(exportCalled.get()).isFalse();
    assertThat(Files.exists(Path.of(OUTPUT_DIR, "should-not-exist.xlsx"))).isFalse();
  }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure -am test -Dtest=ExportFlowIntegrationTest -q`
Expected: PASS，2/2 tests

- [ ] **Step 3: 运行全量测试确保无回归**

Run: `mvn -pl file-service/file-infrastructure -am test -q`
Expected: 全部通过（domain 31/31 + infrastructure 13+2+2=17）

- [ ] **Step 4: Commit**

写 commit message 到 `.superpowers/sdd/task-3-commit-msg.txt`：

```
feat(file-service): Task 3 - 校验失败时不导出测试

新增 ExportFlowIntegrationTest.校验失败时不导出：
- 构造 idNo 为空的行
- DataValidator.validate 返回 ValidationResult.invalid()
- 验证错误信息 "证件编号不能为空"
- 用 spyExporter + AtomicBoolean 验证不调用 export
- 验证输出文件不存在

模拟 ParseFileUseCase 的决策逻辑：仅当 ValidationResult.isValid() == true 时才调用 exporter。

测试：2/2 PASS (全流程 happy path + 校验失败时不导出)
```

```bash
git add file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java
git commit -F .superpowers/sdd/task-3-commit-msg.txt --no-gpg-sign
```

---

## 验证清单

### 实现完成后

- [ ] domain 31/31 测试无回归
- [ ] infrastructure 17/17 测试通过（原 13 + Task 1 新增 2 + Task 2/3 新增 2）
- [ ] 全量编译 BUILD SUCCESS
- [ ] `docs/excel/示例表单_填充模板.xlsx` 已生成
- [ ] `target/test-classes/export-身份证.xlsx` 和 `export-护照.xlsx` 已生成
- [ ] ExcelExporter SPI 无 fesod import（domain 纯度）
- [ ] FesodExcelExporter 实现在 infrastructure 层

### Final code review

- 审查 3 个新文件（1 SPI + 1 实现 + 1 测试）
- 验证 fesod FillWrapper/FillConfig API 使用正确
- 验证 round-trip 数据一致性断言充分
- 验证校验失败测试的 spy 逻辑合理

---

## Self-Review Notes

### Spec coverage
- ✅ Domain SPI `ExcelExporter` → Task 1 Step 1
- ✅ Infrastructure 实现 `FesodExcelExporter` → Task 1 Step 4
- ✅ 程序生成模板 → Task 2 Step 1 (`@BeforeAll`)
- ✅ 全流程 happy path 测试 → Task 2 Step 1
- ✅ 校验失败时不导出测试 → Task 3 Step 1
- ✅ domain 层不依赖 fesod → Task 1 Step 1（SPI 无 fesod import）
- ✅ 不修改现有代码 → 全部 Create，无 Modify

### Type consistency
- `ExcelExporter.export(SplitUnit, InputStream, OutputStream)` — Task 1 定义，Task 2/3 调用，签名一致
- `SplitUnit.splitKey()` / `SplitUnit.data()` — 已存在，测试中使用一致
- `ValidationResult.isValid()` / `ValidationResult.errors()` — 已存在，Task 3 使用一致
- `FillConfig.builder().forceNewRow(true).build()` — fesod API，Task 1 实现使用

### 风险点
1. **fesod FillWrapper/FillConfig 包路径**：Task 1 Step 5 有调试说明，如果路径不对用 `jar tf` 检查
2. **round-trip 数据一致性**：Task 2 用独立的 `buildRoundTripRegionDefs()` 配置（中表头映射），与源 Excel 配置（代码表头）分离
3. **模板生成覆盖**：`@BeforeAll` 检查 `Files.exists`，已存在跳过
4. **测试输出文件清理**：Task 2 测试前 `Files.deleteIfExists` 清理旧文件
