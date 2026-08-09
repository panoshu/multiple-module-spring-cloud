# Excel 导出全流程测试 设计规格

> **日期**: 2026-07-19
> **分支**: `feature/file-service-parse-engine`
> **目标**: 扩展 file-service 解析引擎，新增基于 fesod 模板填充的 Excel 导出能力，并构造从解析、校验、拆分到导出外部表单的端到端全流程测试。

---

## 一、背景与目标

### 1.1 背景

当前 file-service 已具备：

- Excel 解析（`ExcelParser` SPI + `ExcelParserImpl` fesod 实现）
- 区域状态机驱动解析（`RegionStateMachine`）
- 规范数据构建（`CanonicalModelBuilder`）
- 表达式校验（`DataValidator` + `ExpressionEvaluator`）
- 业务键拆分（`TaskSplitter` → `SplitUnit`）

但缺少 **导出能力**：把解析后的规范数据（或拆分后的子任务数据）回填到 Excel 模板，生成可对外分发的表单文件。

### 1.2 目标

1. 在 domain 层定义 `ExcelExporter` Gateway SPI
2. 在 infrastructure 层用 fesod 模板填充实现 `FesodExcelExporter`
3. 程序生成填充模板 `docs/excel/示例表单_填充模板.xlsx`（首次运行创建，已存在跳过）
4. 构造 2 个端到端测试：

- 全流程 happy path（解析 → 校验 → 拆分 → 导出 → round-trip 验证）
- 校验失败时不导出

### 1.3 非目标

- 不实现真实文件存储（`FileStorageGateway` 已存在但本测试不调用）
- 不实现 MQ 集成事件发布（已有 `FileParsedEvent` 但本测试不触发）
- 不修改任何现有代码（纯增量）

---

## 二、架构设计

### 2.1 六边形架构层次

```
file-domain/gateway/ExcelExporter.java              (新增 SPI)
    ↑ 依赖倒置
file-infrastructure/gateway/FesodExcelExporter.java  (新增实现)
    ↑ 调用
file-infrastructure/ExportFlowIntegrationTest.java   (新增测试)
```

### 2.2 数据流

```
docs/excel/示例表单.xlsx
    ↓ ExcelParser.openStream
RawRowStream
    ↓ RegionStateMachine.drive (ParseContext + RegionDef[])
List<RegionParseResult>
    ↓ CanonicalModelBuilder.build
CanonicalData (properties: Map, tables: Map<String, List<Map>>)
    ↓ DataValidator.validate (ExpressionEvaluator)
ValidationResult
    ↓ (passed) TaskSplitter.split (SplitConfig)
List<SplitUnit>  ← 每个 unit.data 是展平的 Map (properties KV + tables List<Map>)
    ↓ FesodExcelExporter.export (template + unit)
docs/excel/示例表单_填充模板.xlsx  →  target/test-classes/export-{splitKey}.xlsx
    ↓ ExcelParser.openStream (round-trip 验证)
重新解析 → 比对数据一致性
```

### 2.3 SplitUnit.data 结构

`TaskSplitter.split` 的输出结构（已存在，本设计不修改）：

```java
SplitUnit.data = {
  // properties (KV 简单变量)
  "customerNo": "000234",
  "customerName": "客户A",
  "filler": "张三",
  "reviewer": "李四",
  // tables (List<Map> 列表变量)
  "employees": [
    {"seq": "1", "name": "张内Aa01", "idType": "身份证", "idNo": "..."},
    {"seq": "2", "name": "...", "idType": "护照", "idNo": "..."}
  ]
}
```

此结构对 fesod 友好：

- 简单变量 → 模板用 `{customerNo}` 占位符
- 列表变量 → 模板用 `{.employees.seq}` 占位符（`FillWrapper` 前缀 = "employees"）

---

## 三、组件设计

### 3.1 Domain SPI — `ExcelExporter`

**文件**: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/ExcelExporter.java`

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
 *
 * <p>实现职责：
 * <ol>
 *   <li>分离 SplitUnit.data 中的简单变量和列表变量</li>
 *   <li>列表用 FillWrapper 包装，前缀 = regionName（如 "employees"）</li>
 *   <li>普通变量用 Map 一次性填充</li>
 *   <li>用 FillConfig.forceNewRow(true) 避免覆盖模板后续内容</li>
 *   <li>try-with-resources 确保 ExcelWriter 关闭</li>
 * </ol>
 */
public interface ExcelExporter {
  void export(SplitUnit unit, InputStream templateStream, OutputStream out);
}
```

### 3.2 Infrastructure 实现 — `FesodExcelExporter`

**文件**:
`file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/FesodExcelExporter.java`

```java
package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.ExcelWriter;
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

**关键点**：

- `FillWrapper(prefix, list)` 让模板用 `{.employees.seq}` 而非 `{.seq}`，支持多列表无歧义
- `FillConfig.forceNewRow(true)` 确保列表填充时新建行，不覆盖底部"填表人"行
- `try-with-resources` 关闭 `ExcelWriter`（fesod 强制要求）

### 3.3 填充模板 `示例表单_填充模板.xlsx`

**位置**: `docs/excel/示例表单_填充模板.xlsx`

**生成方式**: 测试 `@BeforeAll` 程序生成（首次运行创建，已存在跳过）

**模板结构**（6 行 4 列）：

| 行 | A                          | B                   | C                              | D                   |
|----|----------------------------|---------------------|--------------------------------|---------------------|
| 0  | `企业客户号：{customerNo}` |                     | `企业客户名称：{customerName}` |                     |
| 1  | (空)                       |                     |                                |                     |
| 2  | `序号`                     | `姓名`              | `证件类型`                     | `证件号码`          |
| 3  | `{.employees.seq}`         | `{.employees.name}` | `{.employees.idType}`          | `{.employees.idNo}` |
| 4  | (空)                       |                     |                                |                     |
| 5  | `填表人：{filler}`         |                     | `复核人：{reviewer}`           |                     |

**生成代码**（在测试类中）：

```java
private static void ensureFillTemplateExists(Path templatePath) {
  if (Files.exists(templatePath)) return;
  List<List<Object>> templateRows = new ArrayList<>();
  templateRows.add(Arrays.asList("企业客户号：{customerNo}", null, "企业客户名称：{customerName}", null));
  templateRows.add(Arrays.asList(null, null, null, null));
  templateRows.add(Arrays.asList("序号", "姓名", "证件类型", "证件号码"));
  templateRows.add(Arrays.asList("{.employees.seq}", "{.employees.name}", "{.employees.idType}", "{.employees.idNo}"));
  templateRows.add(Arrays.asList(null, null, null, null));
  templateRows.add(Arrays.asList("填表人：{filler}", null, "复核人：{reviewer}", null));
  FesodSheet.write(templatePath.toString()).sheet().doWrite(templateRows);
}
```

### 3.4 测试 `ExportFlowIntegrationTest`

**文件**:
`file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ExportFlowIntegrationTest.java`

#### 测试 1: 全流程 happy path（round-trip）

```java
@Test
void 全流程_解析_校验_拆分_导出_round_trip() throws Exception {
  // 1. 解析示例表单
  CanonicalData data = parseExcel(EXCEL_PATH);

  // 2. 校验通过
  ValidationResult result = validate(data);
  assertThat(result.isValid()).isTrue();

  // 3. 按 idType 拆分
  List<SplitUnit> units = split(data);
  assertThat(units).hasSize(2);  // 身份证 / 护照

  // 4. 每个 SplitUnit 导出
  FesodExcelExporter exporter = new FesodExcelExporter();
  for (SplitUnit unit : units) {
    Path outputPath = Path.of("target", "test-classes",
        "export-" + unit.splitKey() + ".xlsx");
    try (InputStream tpl = new FileInputStream(FILL_TEMPLATE_PATH);
         OutputStream out = new FileOutputStream(outputPath.toFile())) {
      exporter.export(unit, tpl, out);
    }
    assertThat(Files.exists(outputPath)).isTrue();
    assertThat(Files.size(outputPath)).isGreaterThan(0);

    // 5. round-trip: 重新解析导出的 Excel
    CanonicalData reParsed = parseExcel(outputPath.toString());
    // 验证 properties 一致
    assertThat(reParsed.properties())
        .containsEntry("customerNo", unit.data().get("customerNo"))
        .containsEntry("customerName", unit.data().get("customerName"));
    // 验证 tables 行数一致
    List<Map<String, Object>> expectedRows = (List<Map<String, Object>>) unit.data().get("employees");
    assertThat(reParsed.tables().get("employees")).hasSize(expectedRows.size());
    // 验证第一行字段值
    if (!expectedRows.isEmpty()) {
      assertThat(reParsed.tables().get("employees").get(0))
          .containsEntry("seq", expectedRows.get(0).get("seq"))
          .containsEntry("name", expectedRows.get(0).get("name"));
    }
  }
}
```

**round-trip 解析的 RegionDef 配置**：

- KV region: `labelAliases = {customerNo: ["企业客户号："], customerName: ["企业客户名称："], filler: ["填表人："], reviewer: ["复核人："]}`
- Table region: `headerAliases = {seq: ["序号"], name: ["姓名"], idType: ["证件类型"], idNo: ["证件号码"]}`
  (注意：导出模板用中表头，原 Excel 用代码 XH/XM/...)

#### 测试 2: 校验失败时不导出

```java
@Test
void 校验失败时不导出() throws Exception {
  // 构造一个 idNo 为空的行
  Map<String, Object> invalidRow = new LinkedHashMap<>();
  invalidRow.put("seq", "1");
  invalidRow.put("name", "张三");
  invalidRow.put("idType", "身份证");
  invalidRow.put("idNo", null);  // 缺失证件号

  List<ValidationRule> rules = List.of(
      new ValidationRule("idNo", ValidationScope.ROW, "idNo != null",
          "证件编号不能为空", FieldType.STRING));

  ExpressionEvaluator evaluator = (expr, ctx) -> {
    if (expr == null || expr.endsWith("!= null")) {
      String field = expr.substring(0, expr.indexOf("!=")).trim();
      return ctx.containsKey(field) && ctx.get(field) != null;
    }
    return true;
  };

  DataValidator validator = new DataValidator();
  ValidationResult result = validator.validate(invalidRow, rules,
      ErrorPolicy.COLLECT_ALL, evaluator);

  assertThat(result.isValid()).isFalse();
  assertThat(result.errors()).hasSize(1);
  assertThat(result.errors().get(0).message()).isEqualTo("证件编号不能为空");

  // 验证不调用 export：用 spy 或计数器验证
  // 简化实现：直接断言 isValid == false 后 return，不调用 exporter
  // 真实场景：ParseFileUseCase 会检查 isValid 才调用 exporter
}
```

---

## 四、依赖与影响

### 4.1 依赖

- 复用已有 `fesod-sheet` 依赖（file-infrastructure/pom.xml 已声明）
- 复用已有 domain 模型：`SplitUnit`, `CanonicalData`, `ValidationResult`, `ValidationRule`
- 复用已有 domain 服务：`RegionStateMachine`, `CanonicalModelBuilder`, `DataValidator`, `TaskSplitter`
- 复用已有 infrastructure 实现：`ExcelParserImpl`

### 4.2 不修改现有代码

- 纯增量：3 个新文件（1 SPI + 1 实现 + 1 测试）
- 模板文件程序生成，不手动创建

### 4.3 不引入新依赖

- `fesod-sheet` 已在 file-infrastructure/pom.xml
- 无需新增 Maven 依赖

---

## 五、验证标准

### 5.1 功能验证

- [ ] `ExcelExporter` SPI 定义在 file-domain/gateway
- [ ] `FesodExcelExporter` 实现在 file-infrastructure/gateway
- [ ] `@BeforeAll` 生成 `docs/excel/示例表单_填充模板.xlsx`
- [ ] 测试 1: 全流程 happy path 通过（解析 → 校验 → 拆分 → 导出 → round-trip）
- [ ] 测试 2: 校验失败时不导出通过

### 5.2 架构验证

- [ ] domain 层不依赖 fesod（`ExcelExporter` SPI 无 fesod import）
- [ ] infrastructure 层实现 `ExcelExporter` 接口
- [ ] 测试通过 domain 边界（不直接调用 fesod，通过 `ExcelExporter` 接口）

### 5.3 测试结果

- [ ] domain 31/31 测试无回归
- [ ] infrastructure 13+2=15 测试通过（原有 13 + 新增 2）
- [ ] 全量编译 BUILD SUCCESS

---

## 六、风险与缓解

### 6.1 fesod FillWrapper API 兼容性

**风险**: fesod 2.0.x 的 `FillWrapper` 构造函数签名可能与文档不符

**缓解**: 实现时先写一个 smoke test 验证 `FillWrapper("employees", list)` + `FillConfig.forceNewRow(true)` 能正常工作，再写完整测试

### 6.2 round-trip 数据一致性

**风险**: 导出的 Excel 重新解析时，可能因表头差异（模板用中文"序号"，原 Excel 用代码"XH"）导致 round-trip 失败

**缓解**: round-trip 解析使用 **不同的 RegionDef 配置**，`headerAliases` 映射中表头（序号/姓名/...）到标准字段名

### 6.3 模板生成覆盖

**风险**: 每次测试运行都重新生成模板会覆盖手动修改

**缓解**: `@BeforeAll` 检查 `Files.exists(templatePath)`，已存在则跳过。需要重新生成时手动删除文件

### 6.4 测试输出文件清理

**风险**: `target/test-classes/export-*.xlsx` 残留

**缓解**: 测试前 `Files.deleteIfExists` 清理，确保每次测试干净运行
