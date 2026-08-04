# 基于 Excel 的解析测试与遗留项完善 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 `docs/excel/示例表单.xlsx` 创建测试案例，完善 ExcelParserImpl 和 YamlConfigLoader 遗留项，验证解析→校验→拆分完整流程。

**Architecture:** TDD 驱动，分 4 个任务：TableStrategy 增加 headerNameRow + TableRegionParser 完善 DataEndRule →
ExcelParserImpl 修复列索引 + 完善 parse 方法 → YamlConfigLoader 完善完整映射 → 端到端集成测试。

**Tech Stack:** JDK 25, fesod-sheet (Excel), SnakeYAML, JUnit 5, AssertJ

## Global Constraints

- PowerShell 环境，mvn 的 -D 参数用引号：`"-Dmaven.legacyLocalRepo=true"`
- Git commit 用 `--no-gpg-sign` + 临时文件 commit message
- RawRow.cells () 列索引统一为 **0-based**
- 领域对象用 record + 非 JavaBean getter
- 测试用 AssertJ 断言

---

## 值对象签名速查表（实施时参照，防止签名错误）

| 类                      | 构造函数签名                                                                                                                                                                      |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PropertyFieldDef`      | `(String code, FieldType type, boolean required, String pattern)`                                                                                                                 |
| `FieldDef`              | `(String code, FieldType type, boolean required, Integer scale)`                                                                                                                  |
| `TableDef`              | `(String code, List<FieldDef> fields)`                                                                                                                                            |
| `CanonicalModelDef`     | `(List<PropertyFieldDef> properties, List<TableDef> tables)`                                                                                                                      |
| `ValidationRule`        | `(String field, ValidationScope scope, String expr, String message, FieldType type)`                                                                                              |
| `DerivationRule`        | `(String field, String expr, FieldType type, String description)`                                                                                                                 |
| `SplitKeyDef`           | `(String targetField, String sourcePath, SplitKeyType type)` — sourcePath 格式 `"regionName.field"`                                                                               |
| `SplitConfig`           | `(List<String> keys, SplitKeyDef splitKey, SplitMissPolicy onMiss, String defaultOnMissValue, String fileNamingTemplate, boolean promoteToContext, int maxRowsPerSubTask)`        |
| `RegionDef`             | `(String name, RegionType type, String bindTo, RegionTrigger trigger, RegionStrategy strategy)`                                                                                   |
| `RegionTrigger`         | `(TriggerMatchType matchType, int minMatchCount)`                                                                                                                                 |
| `KvStrategy`            | `(KvValuePosition valuePosition, Map<String, List<String>> labelAliases, int maxBlankRows)`                                                                                       |
| `TableStrategy`         | `(int headerRows, int headerNameRow, TableMatchBy matchBy, Map<String, List<String>> headerAliases, HeaderMatching headerMatching, int maxRows, DataEndRule dataEnd)` — Task 1 后 |
| `DataEndRule`           | `(List<String> markers, int blankRowCount)`                                                                                                                                       |
| `SourceTemplateDef`     | `Entity<TemplateCode>` 构造 `(TemplateCode, IdentifyMode, List<String>, List<RegionDef>, UserNo)`                                                                                 |
| `TemplateConfig.create` | `(TemplateConfigId, BizType, String, ErrorPolicy, CanonicalModelDef, List<ValidationRule>, List<DerivationRule>, SplitConfig, List<SourceTemplateDef>, UserNo)`                   |

### 枚举值

| 枚举               | 值                                      |
|--------------------|-----------------------------------------|
| `FieldType`        | STRING, DECIMAL, INTEGER, DATE, BOOLEAN |
| `ValidationScope`  | ROW, GLOBAL                             |
| `SplitKeyType`     | FIELD_VALUE, CONSTANT                   |
| `SplitMissPolicy`  | ERROR, IGNORE, DEFAULT                  |
| `ErrorPolicy`      | FAIL_FAST, COLLECT_ALL, SKIP_ERROR_ROWS |
| `IdentifyMode`     | AUTO, MANUAL                            |
| `RegionType`       | KEY_VALUE, TABLE                        |
| `KvValuePosition`  | RIGHT, BELOW                            |
| `TableMatchBy`     | HEADER_NAME                             |
| `HeaderMatching`   | STRICT, LOOSE                           |
| `TriggerMatchType` | HEADER_SNIFF, REGEX                     |

### 类型工厂方法

| 类型               | 工厂                                   |
|--------------------|----------------------------------------|
| `BizType`          | `BizType.of("ENTERPRISE_PLAN")`        |
| `TemplateCode`     | `TemplateCode.of("STANDARD_TEMPLATE")` |
| `TemplateConfigId` | `TemplateConfigId.of("...")`           |
| `UserNo`           | `UserNo.of("test-user")`               |

### 服务方法签名

| 服务                          | 方法                                                                                                                           |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `DataValidator.validate`      | `(Map<String, Object> data, List<ValidationRule> rules, ErrorPolicy policy, ExpressionEvaluator evaluator) → ValidationResult` |
| `TaskSplitter.split`          | `(Map<String, Object> data, SplitConfig config) → List<SplitUnit>`                                                             |
| `RegionStateMachine.drive`    | `(RawRowStream stream, List<RegionDef> regions, ParseContext ctx) → List<RegionParseResult>`                                   |
| `CanonicalModelBuilder.build` | `(List<RegionParseResult> regions, List<RegionDef> regionDefs) → CanonicalData`                                                |

### 关键接口

| 接口                  | 方法                                                                                                   |
|-----------------------|--------------------------------------------------------------------------------------------------------|
| `ExpressionEvaluator` | `Object evaluate(String expr, Map<String, Object> context)`                                            |
| `ExcelParser`         | `RawRowStream openStream(InputStream)` + `List<RegionParseResult> parse(InputStream, List<RegionDef>)` |
| `ConfigLoader`        | `TemplateConfig loadFromYaml(BizType, String, List<String>, String, UserNo)`                           |

### CanonicalData 结构

```java
public class CanonicalData {
  public Map<String, Object> properties();           // KV 数据
  public Map<String, List<Map<String, Object>>> tables();  // 表格数据
  public static CanonicalData empty();
  public static CanonicalData of(Map<String, Object> properties, Map<String, List<Map<String, Object>>> tables);
}
```

### ValidationResult 方法

```java
public record ValidationResult(List<ValidationError> errors) {
  public boolean passed();
  public boolean isValid();
}
```

### SplitUnit 结构

```java
public record SplitUnit(String splitKey, Map<String, Object> data);
```

---

## 代码调研结论

### 列索引基准不一致（bug）

- `ExcelParserImpl.readAllRows`：`int colIndex = entry.getKey() + 1;` → 产出 **1-based**
- `TableRegionParserTest`：`Map.of(0, "商品编码", ...)` → 期望 **0-based**
- **结论**：ExcelParserImpl 必须修复为 0-based

### KeyValueRegionParser 已支持多组 KV

`matchLabels` 遍历 labelAliases 在所有 cells 中查找， **已天然支持每行多组 KV**，只需配置 labelAliases。

### DataValidator 需要 ExpressionEvaluator

ValidationRule 用 `expr`（表达式字符串），不是 `type: NOT_NULL` 枚举。端到端测试需要提供 ExpressionEvaluator 实现。

**简单 ExpressionEvaluator 实现**（测试用）：

```java
ExpressionEvaluator evaluator = (expr, ctx) -> {
  // 简化：支持 "field != null" 表达式
  if (expr == null) return true;
  if (expr.endsWith("!= null")) {
    String field = expr.substring(0, expr.indexOf("!=")).trim();
    return ctx.containsKey(field) && ctx.get(field) != null;
  }
  return true;
};
```

### TaskSplitter 的 sourcePath 格式

`SplitKeyDef.sourcePath` 格式是 `"regionName.field"`，如 `"employees.customerNo"`。TaskSplitter 会从
`data.get(regionName)` 取 List<Map>，按 field 分组。

### 端到端测试数据流

```
Excel → ExcelParser.openStream → RawRowStream
  → RegionStateMachine.drive(regions) → List<RegionParseResult>
  → CanonicalModelBuilder.build → CanonicalData
  → 转换为 Map<String, Object>（properties + tables 嵌套）
  → DataValidator.validate（需要 ExpressionEvaluator）
  → TaskSplitter.split（SplitConfig.splitKey.sourcePath = "employees.customerNo"）
  → List<SplitUnit>
```

CanonicalData → Map 转换：

```java
Map<String, Object> dataMap = new LinkedHashMap<>();
dataMap.putAll(canonicalData.properties());
dataMap.putAll(canonicalData.tables());  // tables 的 key 是 regionName，value 是 List<Map>
```

---

## File Structure

| 文件                                                        | 操作 | 职责                                     |
|-------------------------------------------------------------|------|------------------------------------------|
| `file-domain/.../config/TableStrategy.java`                 | 修改 | 增加 headerNameRow 字段                  |
| `file-domain/.../service/TableRegionParser.java`            | 修改 | 支持 headerNameRow + DataEndRule.markers |
| `file-domain/.../service/TableRegionParserTest.java`        | 修改 | 适配新构造函数 + 新增测试                |
| `file-domain/.../service/CanonicalModelBuilderTest.java`    | 修改 | 适配新构造函数                           |
| `file-infrastructure/.../gateway/ExcelParserImpl.java`      | 修改 | 修复列索引 + 完善 parse 方法             |
| `file-infrastructure/.../gateway/ExcelParserImplTest.java`  | 新建 | 解析层单元测试                           |
| `file-infrastructure/.../gateway/YamlConfigLoader.java`     | 修改 | 完善完整 YAML → TemplateConfig 映射      |
| `file-infrastructure/.../gateway/YamlConfigLoaderTest.java` | 新建 | ConfigLoader 单元测试                    |
| `file-infrastructure/.../ParseFlowIntegrationTest.java`     | 新建 | 端到端集成测试                           |

---

### Task 1: TableStrategy 增加 headerNameRow + TableRegionParser 完善

**Files:**

- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/model/valueobject/config/TableStrategy.java`
- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/service/TableRegionParser.java`
- Modify: `file-service/file-domain/src/test/java/com/example/file/domain/service/TableRegionParserTest.java`
- Modify: `file-service/file-domain/src/test/java/com/example/file/domain/service/CanonicalModelBuilderTest.java`

- [ ] **Step 1: 修改 TableStrategy record 增加 headerNameRow**

修改 `file-service/file-domain/src/main/java/com/example/file/domain/model/valueobject/config/TableStrategy.java`：

```java
package com.example.file.domain.model.valueobject.config;

import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.TableMatchBy;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;
import java.util.Map;

public record TableStrategy(
    int headerRows,
    int headerNameRow,
    TableMatchBy matchBy,
    Map<String, List<String>> headerAliases,
    HeaderMatching headerMatching,
    int maxRows,
    DataEndRule dataEnd
) implements RegionStrategy, ValueObject {
  public TableStrategy {
    if (headerRows <= 0) headerRows = 1;
    if (headerNameRow < 0) headerNameRow = 0;
    if (headerNameRow > headerRows) headerNameRow = headerRows;
    matchBy = matchBy == null ? TableMatchBy.HEADER_NAME : matchBy;
    headerAliases = headerAliases == null ? Map.of() : Map.copyOf(headerAliases);
    headerMatching = headerMatching == null ? HeaderMatching.STRICT : headerMatching;
    if (maxRows < 0) maxRows = 0;
  }
}
```

- [ ] **Step 2: 修改 TableRegionParser 支持 headerNameRow 和 DataEndRule.markers**

修改 `file-service/file-domain/src/main/java/com/example/file/domain/service/TableRegionParser.java`：

```java
package com.example.file.domain.service;

import com.example.shared.domain.annotation.DomainService;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;

import java.util.*;

@DomainService
public class TableRegionParser implements RegionParser {

  @Override
  public RegionType supportedType() { return RegionType.TABLE; }

  @Override
  public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
    TableStrategy strategy = (TableStrategy) regionDef.strategy();
    List<String> headers = new ArrayList<>();
    List<Map<String, Object>> rows = new ArrayList<>();
    int dataRowCount = 0;
    int headerRowsRead = 0;
    int nameRowIdx = strategy.headerNameRow() == 0
        ? strategy.headerRows() - 1
        : strategy.headerNameRow() - 1;

    while (stream.hasNext()) {
      RawRow row = stream.peek();
      if (row.isBlank()) { stream.next(); continue; }
      if (ctx.isNextRegionTrigger(row)) break;

      stream.next();
      if (headerRowsRead < strategy.headerRows()) {
        if (headerRowsRead == nameRowIdx) {
          headers = extractHeaders(row, strategy);
        }
        headerRowsRead++;
        continue;
      }
      if (strategy.maxRows() > 0 && dataRowCount >= strategy.maxRows()) break;
      if (isDataEnd(row, strategy.dataEnd())) break;
      Map<String, Object> dataRow = mapDataRow(row, headers);
      if (!dataRow.isEmpty()) {
        rows.add(dataRow);
        dataRowCount++;
      }
    }
    return new TableRegionResult(regionDef.name(), headers, rows);
  }

  private boolean isDataEnd(RawRow row, DataEndRule dataEnd) {
    if (dataEnd == null || dataEnd.markers().isEmpty()) return false;
    String firstCell = row.cells().get(0);
    if (firstCell == null) return false;
    return dataEnd.markers().contains(firstCell.trim());
  }

  private List<String> extractHeaders(RawRow row, TableStrategy strategy) {
    List<String> headers = new ArrayList<>();
    Map<Integer, String> cells = row.cells();
    int maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    for (int i = 0; i <= maxColIdx; i++) {
      String cellValue = cells.get(i);
      if (cellValue == null || cellValue.isBlank()) { headers.add(null); continue; }
      String canonical = strategy.headerAliases().entrySet().stream()
          .filter(e -> e.getValue().contains(cellValue))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(HeaderMatching.STRICT.equals(strategy.headerMatching()) ? null : cellValue);
      headers.add(canonical);
    }
    return headers;
  }

  private Map<String, Object> mapDataRow(RawRow row, List<String> headers) {
    Map<String, Object> dataRow = new LinkedHashMap<>();
    Map<Integer, String> cells = row.cells();
    int maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    int maxIdx = Math.min(headers.size() - 1, maxColIdx);
    for (int i = 0; i <= maxIdx; i++) {
      String key = headers.get(i);
      if (key != null) dataRow.put(key, cells.get(i));
    }
    return dataRow;
  }
}
```

- [ ] **Step 3: 更新 TableRegionParserTest 适配新构造函数 + 新增测试**

修改 `file-service/file-domain/src/test/java/com/example/file/domain/service/TableRegionParserTest.java`：

```java
package com.example.file.domain.service;

import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.enums.TableMatchBy;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TableRegionParserTest {

  @Test
  void should_parse_table_with_header_and_data_rows() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            1, 0, TableMatchBy.HEADER_NAME,
            Map.of("code", List.of("商品编码"), "name", List.of("商品名称"), "qty", List.of("数量")),
            HeaderMatching.STRICT, 100, null));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "商品编码", 1, "商品名称", 2, "数量"), false),
        RawRow.of(1, Map.of(0, "A1", 1, "苹果", 2, "10"), false),
        RawRow.of(2, Map.of(0, "A2", 1, "香蕉", 2, "20"), false),
        RawRow.of(3, Map.of(), true)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.headers()).containsExactly("code", "name", "qty");
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0)).containsEntry("code", "A1");
    assertThat(result.rows().get(1)).containsEntry("qty", "20");
  }

  @Test
  void should_use_headerNameRow_to_select_header_row() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            3, 1, TableMatchBy.HEADER_NAME,
            Map.of("seq", List.of("XH"), "name", List.of("XM")),
            HeaderMatching.STRICT, 100, null));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "XH", 1, "XM"), false),
        RawRow.of(1, Map.of(0, "基本信息"), false),
        RawRow.of(2, Map.of(0, "序号*", 1, "个人姓名*"), false),
        RawRow.of(3, Map.of(0, "1", 1, "张三"), false),
        RawRow.of(4, Map.of(), true)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.headers()).containsExactly("seq", "name");
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0)).containsEntry("seq", "1");
    assertThat(result.rows().get(0)).containsEntry("name", "张三");
  }

  @Test
  void should_stop_at_dataEnd_marker() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            1, 0, TableMatchBy.HEADER_NAME,
            Map.of("code", List.of("code")),
            HeaderMatching.STRICT, 100,
            new DataEndRule(List.of("结束"), 1)));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "code"), false),
        RawRow.of(1, Map.of(0, "A1"), false),
        RawRow.of(2, Map.of(0, "结束", 1, "说明文字"), false),
        RawRow.of(3, Map.of(0, "A2"), false)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0)).containsEntry("code", "A1");
  }

  static class FakeStream implements RawRowStream {
    private final List<RawRow> rows;
    private int idx = 0;
    FakeStream(List<RawRow> rows) { this.rows = rows; }
    @Override public boolean hasNext() { return idx < rows.size(); }
    @Override public RawRow next() { return rows.get(idx++); }
    @Override public RawRow peek() { return idx < rows.size() ? rows.get(idx) : rows.get(rows.size() - 1); }
    @Override public int currentRowIndex() { return idx == 0 ? -1 : rows.get(idx - 1).rowIndex(); }
  }
}
```

- [ ] **Step 4: 更新 CanonicalModelBuilderTest 适配新构造函数**

修改 `file-service/file-domain/src/test/java/com/example/file/domain/service/CanonicalModelBuilderTest.java`：

第 27 行（items 表格的 TableStrategy）改为：

```java
new TableStrategy(1, 0, null, Map.of(), null, 0, null)
```

- [ ] **Step 5: 编译并运行 domain 层测试**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-domain test`
Expected: BUILD SUCCESS, 30 tests（原 28 + 新增 2）

- [ ] **Step 6: 提交**

```powershell
$msgPath = Join-Path $env:TEMP "commit-msg.txt"
@"
feat(file-domain): add headerNameRow to TableStrategy + DataEndRule markers support

TableStrategy: add headerNameRow field (0=last row compat, N=1-based row index)
TableRegionParser: use headerNameRow + add DataEndRule.markers detection
Tests: update existing + add headerNameRow and dataEnd marker tests
"@ | Out-File -FilePath $msgPath -Encoding utf8
git add file-service/file-domain/src/
git commit --no-gpg-sign -F $msgPath
Remove-Item $msgPath
```

---

### Task 2: ExcelParserImpl 修复列索引 + 完善 parse 方法

**Files:**

- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/ExcelParserImpl.java`
- Create:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/ExcelParserImplTest.java`

- [ ] **Step 1: 写 ExcelParserImplTest 失败测试**

创建 `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/ExcelParserImplTest.java`：

```java
package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.enums.TableMatchBy;
import com.example.file.domain.model.enums.TriggerMatchType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.RegionTrigger;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelParserImplTest {

  private static final String EXCEL_PATH =
      Path.of("docs", "excel", "示例表单.xlsx").toString();

  private final ExcelParser parser = new ExcelParserImpl();

  @Test
  void openStream_读取所有行() throws Exception {
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      RawRowStream stream = parser.openStream(is);

      int count = 0;
      while (stream.hasNext()) {
        RawRow row = stream.next();
        if (count == 0) {
          assertThat(row.isBlank()).isFalse();
          assertThat(row.cells().get(0)).isEqualTo("企业计划编号：");
        }
        if (count == 2) {
          assertThat(row.isBlank()).isTrue();
        }
        count++;
      }
      assertThat(count).isEqualTo(15);
    }
  }

  @Test
  void parse_KV区域_多组label_value() throws Exception {
    List<RegionDef> regions = List.of(
        new RegionDef("basic_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "planNo", List.of("企业计划编号："),
                    "planName", List.of("企业计划名称："),
                    "customerNo", List.of("企业客户号："),
                    "customerName", List.of("企业客户名称：")),
                2)));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      KvRegionResult kv = (KvRegionResult) results.get(0);
      assertThat(kv.data()).containsEntry("planNo", "0200010001");
      assertThat(kv.data()).containsEntry("planName", "企业计划A");
      assertThat(kv.data()).containsEntry("customerNo", "000234");
      assertThat(kv.data()).containsEntry("customerName", "客户A");
    }
  }

  @Test
  void parse_表格区域_headerNameRow_1_并映射标准字段() throws Exception {
    List<RegionDef> regions = List.of(
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
                new DataEndRule(List.of("结束"), 1))));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      TableRegionResult table = (TableRegionResult) results.get(0);
      assertThat(table.headers()).contains("seq", "name", "idType", "idNo");
      assertThat(table.rows()).hasSize(3);
      assertThat(table.rows().get(0)).containsEntry("seq", "1");
      assertThat(table.rows().get(0)).containsEntry("name", "张内Aa01");
      assertThat(table.rows().get(0)).containsEntry("idType", "身份证");
    }
  }

  @Test
  void parse_表格区域_结束标记停止() throws Exception {
    List<RegionDef> regions = List.of(
        new RegionDef("employee_list", RegionType.TABLE, "employees",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 5),
            new TableStrategy(
                3, 1, TableMatchBy.HEADER_NAME,
                Map.of("seq", List.of("XH")),
                HeaderMatching.STRICT, 0,
                new DataEndRule(List.of("结束"), 1))));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      TableRegionResult table = (TableRegionResult) results.get(0);
      assertThat(table.rows()).hasSize(3);
      assertThat(table.rows().get(0).get("seq")).isEqualTo("1");
      assertThat(table.rows().get(2).get("seq")).isEqualTo("3");
    }
  }

  @Test
  void parse_KV区域2_填表人() throws Exception {
    List<RegionDef> regions = List.of(
        new RegionDef("filler_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 1),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "filler", List.of("填表人:"),
                    "reviewer", List.of("复核人：")),
                1)));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      KvRegionResult kv = (KvRegionResult) results.get(0);
      assertThat(kv.data()).containsEntry("filler", "张三");
      assertThat(kv.data()).containsEntry("reviewer", "李四");
    }
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test -Dtest=ExcelParserImplTest`
Expected: FAIL（列索引错位 + parse 方法不支持 headerNameRow/headerAliases/markers）

- [ ] **Step 3: 修复 ExcelParserImpl 列索引 + 完善 parse 方法**

修改 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/ExcelParserImpl.java`：

```java
package com.example.file.infrastructure.gateway;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import com.example.file.domain.model.valueobject.parse.RegionSkip;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class ExcelParserImpl implements ExcelParser {

  @Override
  public RawRowStream openStream(InputStream excelStream) {
    List<RawRow> rows = readAllRows(excelStream);
    return new ListBackedRawRowStream(rows);
  }

  @Override
  public List<RegionParseResult> parse(InputStream excelStream, List<RegionDef> regions) {
    List<RawRow> allRows = readAllRows(excelStream);
    List<RegionParseResult> results = new ArrayList<>();
    int cursor = 0;

    for (RegionDef region : regions) {
      int triggerRow = findTriggerRow(allRows, cursor, region);
      if (triggerRow < 0) {
        results.add(new RegionSkip());
        continue;
      }

      if (region.type() == RegionType.KEY_VALUE) {
        KvRegionResult kvResult = parseKvRegion(allRows, triggerRow, region);
        results.add(kvResult);
        cursor = advancePastKvRegion(allRows, triggerRow, region);
      } else if (region.type() == RegionType.TABLE) {
        TableRegionResult tableResult = parseTableRegion(allRows, triggerRow, region);
        results.add(tableResult);
        cursor = advancePastTableRegion(allRows, triggerRow, region);
      } else {
        results.add(new RegionSkip());
      }
    }

    return results;
  }

  private List<RawRow> readAllRows(InputStream is) {
    List<RawRow> rows = new ArrayList<>();
    FesodSheet.read(is, new ReadListener<Map<Integer, String>>() {
      @Override
      public void invoke(Map<Integer, String> data, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;
        Map<Integer, String> cells = new HashMap<>();
        boolean isBlank = true;
        if (data != null) {
          for (Map.Entry<Integer, String> entry : data.entrySet()) {
            // 0-based 列索引，与 domain 层一致
            int colIndex = entry.getKey();
            String val = entry.getValue();
            cells.put(colIndex, val);
            if (val != null && !val.trim().isEmpty()) {
              isBlank = false;
            }
          }
        }
        rows.add(new RawRow(rowIndex, cells, isBlank));
      }

      @Override
      public void doAfterAllAnalysed(AnalysisContext context) {
      }
    }).sheet().doRead();
    return rows;
  }

  private int findTriggerRow(List<RawRow> rows, int startIndex, RegionDef region) {
    if (region.trigger() == null) {
      return startIndex;
    }
    for (int i = startIndex; i < rows.size(); i++) {
      RawRow row = rows.get(i);
      if (matchesTrigger(row, region)) {
        return i;
      }
    }
    return -1;
  }

  private boolean matchesTrigger(RawRow row, RegionDef region) {
    if (region.trigger() == null) return true;
    if (row.isBlank()) return false;
    long matchCount = row.cells().values().stream()
        .filter(v -> v != null && !v.trim().isEmpty())
        .count();
    return matchCount >= region.trigger().minMatchCount();
  }

  private KvRegionResult parseKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    KvStrategy strategy = (KvStrategy) region.strategy();
    Map<String, Object> data = new LinkedHashMap<>();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;

    int i = triggerRow;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) {
        blankCount++;
        if (blankCount >= maxBlankRows) break;
        i++;
        continue;
      }
      blankCount = 0;

      // 基于 labelAliases 匹配，支持每行多组 KV
      Map<Integer, String> cells = row.cells();
      for (Map.Entry<String, List<String>> entry : strategy.labelAliases().entrySet()) {
        String canonicalKey = entry.getKey();
        List<String> aliases = entry.getValue();
        for (Map.Entry<Integer, String> cell : cells.entrySet()) {
          int colIdx = cell.getKey();
          String cellValue = cell.getValue();
          if (aliases.contains(cellValue) && strategy.valuePosition() == KvValuePosition.RIGHT) {
            String value = cells.get(colIdx + 1);
            if (value != null) {
              data.put(canonicalKey, value.trim());
            }
          }
        }
      }
      i++;
    }

    return new KvRegionResult(region.name(), data);
  }

  private int advancePastKvRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    KvStrategy strategy = (KvStrategy) region.strategy();
    int maxBlankRows = strategy.maxBlankRows();
    int blankCount = 0;
    int i = triggerRow;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) {
        blankCount++;
        if (blankCount >= maxBlankRows) break;
      } else {
        blankCount = 0;
      }
      i++;
    }
    return i;
  }

  private TableRegionResult parseTableRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    TableStrategy strategy = (TableStrategy) region.strategy();
    int headerRows = strategy.headerRows();
    int nameRowIdx = strategy.headerNameRow() == 0
        ? headerRows - 1
        : strategy.headerNameRow() - 1;

    int headerStart = triggerRow + 1;
    List<String> headers = new ArrayList<>();
    int maxColIdx = 0;

    // 读取表头行
    for (int h = 0; h < headerRows; h++) {
      int rowIdx = headerStart + h;
      if (rowIdx >= rows.size()) break;
      if (h == nameRowIdx) {
        RawRow headerRow = rows.get(rowIdx);
        Map<Integer, String> cells = headerRow.cells();
        maxColIdx = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int col = 0; col <= maxColIdx; col++) {
          String cellValue = cells.get(col);
          if (cellValue == null || cellValue.isBlank()) {
            headers.add(null);
            continue;
          }
          String canonical = strategy.headerAliases().entrySet().stream()
              .filter(e -> e.getValue().contains(cellValue))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse(HeaderMatching.STRICT.equals(strategy.headerMatching()) ? null : cellValue);
          headers.add(canonical);
        }
      }
    }

    // 读取数据行
    List<Map<String, Object>> dataRows = new ArrayList<>();
    int maxRows = strategy.maxRows();
    int rowCount = 0;
    int i = headerStart + headerRows;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;
      if (isDataEnd(row, strategy.dataEnd())) break;

      Map<String, Object> rowData = new LinkedHashMap<>();
      Map<Integer, String> cells = row.cells();
      for (int col = 0; col <= Math.min(headers.size() - 1, maxColIdx); col++) {
        String header = headers.get(col);
        if (header != null) {
          String val = cells.get(col);
          rowData.put(header, val != null ? val.trim() : "");
        }
      }
      if (!rowData.isEmpty()) {
        dataRows.add(rowData);
        rowCount++;
      }
      i++;
    }

    return new TableRegionResult(region.name(), headers, dataRows);
  }

  private boolean isDataEnd(RawRow row, DataEndRule dataEnd) {
    if (dataEnd == null || dataEnd.markers().isEmpty()) return false;
    String firstCell = row.cells().get(0);
    if (firstCell == null) return false;
    return dataEnd.markers().contains(firstCell.trim());
  }

  private int advancePastTableRegion(List<RawRow> rows, int triggerRow, RegionDef region) {
    TableStrategy strategy = (TableStrategy) region.strategy();
    int headerRows = strategy.headerRows();
    int maxRows = strategy.maxRows();
    int headerStart = triggerRow + 1;
    int i = headerStart + headerRows;
    int rowCount = 0;
    while (i < rows.size()) {
      RawRow row = rows.get(i);
      if (row.isBlank()) break;
      if (maxRows > 0 && rowCount >= maxRows) break;
      if (isDataEnd(row, strategy.dataEnd())) break;
      rowCount++;
      i++;
    }
    return i;
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test -Dtest=ExcelParserImplTest`
Expected: BUILD SUCCESS, 5 tests pass

- [ ] **Step 5: 提交**

```powershell
$msgPath = Join-Path $env:TEMP "commit-msg.txt"
@"
feat(file-infrastructure): fix column index to 0-based + enhance ExcelParserImpl

- Fix column index from 1-based to 0-based (align with domain layer)
- parse: use headerNameRow for multi-row header selection
- parse: use headerAliases for column name mapping
- parse: use DataEndRule.markers for end-of-data detection
- parseKvRegion: use labelAliases for KV matching (supports multi-group)
- 5 test cases based on docs/excel/示例表单.xlsx
"@ | Out-File -FilePath $msgPath -Encoding utf8
git add file-service/file-infrastructure/src/
git commit --no-gpg-sign -F $msgPath
Remove-Item $msgPath
```

---

### Task 3: YamlConfigLoader 完善 + 测试

**Files:**

- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/YamlConfigLoader.java`
- Create:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/YamlConfigLoaderTest.java`

- [ ] **Step 1: 写 YamlConfigLoaderTest 失败测试**

创建 `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/YamlConfigLoaderTest.java`：

```java
package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.types.BizType;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigLoaderTest {

  private final ConfigLoader loader = new YamlConfigLoader();

  private static final String BASELINE_YAML = """
      bizType: ENTERPRISE_PLAN
      version: "1.0"
      errorPolicy: COLLECT_ALL

      canonicalModel:
        properties:
          - { code: planNo, IdentityType: STRING, required: true }
          - { code: planName, IdentityType: STRING, required: true }
          - { code: customerNo, IdentityType: STRING, required: true }
          - { code: customerName, IdentityType: STRING, required: true }
          - { code: filler, IdentityType: STRING }
          - { code: reviewer, IdentityType: STRING }
        tables:
          - code: employees
            fields:
              - { code: seq, IdentityType: INTEGER, required: true }
              - { code: name, IdentityType: STRING, required: true }
              - { code: idType, IdentityType: STRING, required: true }
              - { code: idNo, IdentityType: STRING, required: true }

      validationRules:
        - { field: idNo, scope: ROW, expr: "idNo != null", message: "证件编号不能为空", IdentityType: STRING }
        - { field: name, scope: ROW, expr: "name != null", message: "姓名不能为空", IdentityType: STRING }

      derivationRules: []

      splitConfig:
        keys: [customerNo]
        splitKey:
          targetField: customerNo
          sourcePath: employees.customerNo
          IdentityType: FIELD_VALUE
        onMiss: ERROR
        maxRowsPerSubTask: 1000
      """;

  private static final String SOURCE_TEMPLATE_YAML = """
      id: STANDARD_TEMPLATE
      name: "企业计划标准模板"
      identifyMode: AUTO
      fingerprint: ["企业计划编号：", "企业客户号：", "序号*"]
      regions:
        - name: basic_info
          IdentityType: KEY_VALUE
          bindTo: properties
          trigger: { matchType: HEADER_SNIFF, minMatchCount: 2 }
          strategy:
            valuePosition: RIGHT
            labelAliases:
              planNo: ["企业计划编号："]
              planName: ["企业计划名称："]
              customerNo: ["企业客户号："]
              customerName: ["企业客户名称："]
            maxBlankRows: 2
        - name: employee_list
          IdentityType: TABLE
          bindTo: employees
          trigger: { matchType: HEADER_SNIFF, minMatchCount: 5 }
          strategy:
            headerRows: 3
            headerNameRow: 1
            headerAliases:
              seq: [XH]
              name: [XM]
              idType: [ZJLX]
              idNo: [ZJHM]
            dataEnd: { markers: ["结束"], blankRowCount: 1 }
        - name: filler_info
          IdentityType: KEY_VALUE
          bindTo: properties
          trigger: { matchType: HEADER_SNIFF, minMatchCount: 1 }
          strategy:
            valuePosition: RIGHT
            labelAliases:
              filler: ["填表人:"]
              reviewer: ["复核人："]
            maxBlankRows: 1
      """;

  @Test
  void loadFromYaml_基础字段() {
    TemplateConfig config = loader.loadFromYaml(
        BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
        List.of(SOURCE_TEMPLATE_YAML), "1.0",
        UserNo.of("test-user"));

    assertThat(config.bizType().value()).isEqualTo("ENTERPRISE_PLAN");
    assertThat(config.templateVersion()).isEqualTo("1.0");
    assertThat(config.errorPolicy().name()).isEqualTo("COLLECT_ALL");
  }

  @Test
  void loadFromYaml_规范模型() {
    TemplateConfig config = loader.loadFromYaml(
        BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
        List.of(SOURCE_TEMPLATE_YAML), "1.0",
        UserNo.of("test-user"));

    assertThat(config.canonicalModel().properties()).hasSize(6);
    assertThat(config.canonicalModel().tables()).hasSize(1);
    assertThat(config.canonicalModel().tables().get(0).code()).isEqualTo("employees");
  }

  @Test
  void loadFromYaml_校验规则() {
    TemplateConfig config = loader.loadFromYaml(
        BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
        List.of(SOURCE_TEMPLATE_YAML), "1.0",
        UserNo.of("test-user"));

    assertThat(config.validationRules()).hasSize(2);
    assertThat(config.validationRules().get(0).field()).isEqualTo("idNo");
    assertThat(config.validationRules().get(0).expr()).isEqualTo("idNo != null");
  }

  @Test
  void loadFromYaml_拆分配置() {
    TemplateConfig config = loader.loadFromYaml(
        BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
        List.of(SOURCE_TEMPLATE_YAML), "1.0",
        UserNo.of("test-user"));

    assertThat(config.splitConfig().keys()).contains("customerNo");
    assertThat(config.splitConfig().splitKey().sourcePath()).isEqualTo("employees.customerNo");
    assertThat(config.splitConfig().maxRowsPerSubTask()).isEqualTo(1000);
  }

  @Test
  void loadFromYaml_源模板() {
    TemplateConfig config = loader.loadFromYaml(
        BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
        List.of(SOURCE_TEMPLATE_YAML), "1.0",
        UserNo.of("test-user"));

    assertThat(config.sourceTemplates()).hasSize(1);
    assertThat(config.sourceTemplates().get(0).id().value()).isEqualTo("STANDARD_TEMPLATE");
    assertThat(config.sourceTemplates().get(0).regions()).hasSize(3);
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test -Dtest=YamlConfigLoaderTest`
Expected: FAIL（当前 YamlConfigLoader 返回空对象）

- [ ] **Step 3: 完善 YamlConfigLoader**

修改 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/gateway/YamlConfigLoader.java`：

```java
package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.types.BizType;
import com.example.file.types.TemplateCode;
import com.example.file.types.TemplateConfigId;
import com.example.shared.primitives.identity.UserNo;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class YamlConfigLoader implements ConfigLoader {

  @Override
  public TemplateConfig loadFromYaml(BizType bizType, String baselineYaml,
                                     List<String> sourceTemplateYamls, String version,
                                     UserNo operator) {
    Yaml yaml = new Yaml();

    Map<String, Object> baseline = yaml.load(baselineYaml);

    List<Map<String, Object>> sourceTemplates = new ArrayList<>();
    for (String sourceYaml : sourceTemplateYamls) {
      Map<String, Object> source = yaml.load(sourceYaml);
      sourceTemplates.add(source);
    }

    return buildTemplateConfig(bizType, baseline, sourceTemplates, version, operator);
  }

  @SuppressWarnings("unchecked")
  private TemplateConfig buildTemplateConfig(BizType bizType, Map<String, Object> baseline,
                                             List<Map<String, Object>> sourceTemplates,
                                             String version, UserNo operator) {
    TemplateConfigId id = TemplateConfigId.of(generateId());

    ErrorPolicy errorPolicy = parseEnum(baseline, "errorPolicy", ErrorPolicy.class, ErrorPolicy.FAIL_FAST);

    CanonicalModelDef canonicalModel = parseCanonicalModel(baseline);

    List<ValidationRule> validationRules = parseValidationRules(baseline);

    List<DerivationRule> derivationRules = parseDerivationRules(baseline);

    SplitConfig splitConfig = parseSplitConfig(baseline);

    List<SourceTemplateDef> sourceTemplateDefs = new ArrayList<>();
    for (Map<String, Object> st : sourceTemplates) {
      sourceTemplateDefs.add(parseSourceTemplate(st, operator));
    }

    return TemplateConfig.create(
        id, bizType, version, errorPolicy, canonicalModel,
        validationRules, derivationRules, splitConfig,
        sourceTemplateDefs, operator
    );
  }

  @SuppressWarnings("unchecked")
  private CanonicalModelDef parseCanonicalModel(Map<String, Object> baseline) {
    Map<String, Object> cm = (Map<String, Object>) baseline.get("canonicalModel");
    if (cm == null) return new CanonicalModelDef(List.of(), List.of());

    List<PropertyFieldDef> properties = new ArrayList<>();
    List<Map<String, Object>> props = (List<Map<String, Object>>) cm.getOrDefault("properties", List.of());
    for (Map<String, Object> p : props) {
      properties.add(new PropertyFieldDef(
          (String) p.get("code"),
          parseEnum(p, "type", FieldType.class, FieldType.STRING),
          Boolean.TRUE.equals(p.get("required")),
          (String) p.get("pattern")));
    }

    List<TableDef> tables = new ArrayList<>();
    List<Map<String, Object>> tbls = (List<Map<String, Object>>) cm.getOrDefault("tables", List.of());
    for (Map<String, Object> t : tbls) {
      List<FieldDef> fields = new ArrayList<>();
      List<Map<String, Object>> flds = (List<Map<String, Object>>) t.getOrDefault("fields", List.of());
      for (Map<String, Object> f : flds) {
        Object scaleObj = f.get("scale");
        Integer scale = scaleObj instanceof Number ? ((Number) scaleObj).intValue() : null;
        fields.add(new FieldDef(
            (String) f.get("code"),
            parseEnum(f, "type", FieldType.class, FieldType.STRING),
            Boolean.TRUE.equals(f.get("required")),
            scale));
      }
      tables.add(new TableDef((String) t.get("code"), fields));
    }

    return new CanonicalModelDef(properties, tables);
  }

  @SuppressWarnings("unchecked")
  private List<ValidationRule> parseValidationRules(Map<String, Object> baseline) {
    List<ValidationRule> rules = new ArrayList<>();
    List<Map<String, Object>> list = (List<Map<String, Object>>) baseline.getOrDefault("validationRules", List.of());
    for (Map<String, Object> r : list) {
      rules.add(new ValidationRule(
          (String) r.get("field"),
          parseEnum(r, "scope", ValidationScope.class, ValidationScope.ROW),
          (String) r.get("expr"),
          (String) r.get("message"),
          parseEnum(r, "type", FieldType.class, FieldType.STRING)));
    }
    return rules;
  }

  @SuppressWarnings("unchecked")
  private List<DerivationRule> parseDerivationRules(Map<String, Object> baseline) {
    List<DerivationRule> rules = new ArrayList<>();
    List<Map<String, Object>> list = (List<Map<String, Object>>) baseline.getOrDefault("derivationRules", List.of());
    for (Map<String, Object> r : list) {
      rules.add(new DerivationRule(
          (String) r.get("field"),
          (String) r.get("expr"),
          parseEnum(r, "type", FieldType.class, FieldType.STRING),
          (String) r.get("description")));
    }
    return rules;
  }

  @SuppressWarnings("unchecked")
  private SplitConfig parseSplitConfig(Map<String, Object> baseline) {
    Map<String, Object> sc = (Map<String, Object>) baseline.get("splitConfig");
    if (sc == null) return new SplitConfig(List.of(), null, SplitMissPolicy.ERROR, null, null, false, 0);

    List<String> keys = (List<String>) sc.getOrDefault("keys", List.of());

    Map<String, Object> sk = (Map<String, Object>) sc.get("splitKey");
    SplitKeyDef splitKey = null;
    if (sk != null) {
      splitKey = new SplitKeyDef(
          (String) sk.get("targetField"),
          (String) sk.get("sourcePath"),
          parseEnum(sk, "type", SplitKeyType.class, SplitKeyType.FIELD_VALUE));
    }

    SplitMissPolicy onMiss = parseEnum(sc, "onMiss", SplitMissPolicy.class, SplitMissPolicy.ERROR);
    Object maxRows = sc.get("maxRowsPerSubTask");
    int maxRowsPerSubTask = maxRows instanceof Number ? ((Number) maxRows).intValue() : 0;

    return new SplitConfig(keys, splitKey, onMiss,
        (String) sc.get("defaultOnMissValue"),
        (String) sc.get("fileNamingTemplate"),
        Boolean.TRUE.equals(sc.get("promoteToContext")),
        maxRowsPerSubTask);
  }

  @SuppressWarnings("unchecked")
  private SourceTemplateDef parseSourceTemplate(Map<String, Object> st, UserNo operator) {
    TemplateCode code = TemplateCode.of((String) st.get("id"));
    IdentifyMode mode = parseEnum(st, "identifyMode", IdentifyMode.class, IdentifyMode.AUTO);
    List<String> fingerprint = (List<String>) st.getOrDefault("fingerprint", List.of());

    List<RegionDef> regions = new ArrayList<>();
    List<Map<String, Object>> regionList = (List<Map<String, Object>>) st.getOrDefault("regions", List.of());
    for (Map<String, Object> r : regionList) {
      regions.add(parseRegion(r));
    }

    return new SourceTemplateDef(code, mode, fingerprint, regions, operator);
  }

  @SuppressWarnings("unchecked")
  private RegionDef parseRegion(Map<String, Object> r) {
    String name = (String) r.get("name");
    RegionType type = parseEnum(r, "type", RegionType.class, RegionType.KEY_VALUE);
    String bindTo = (String) r.get("bindTo");

    RegionTrigger trigger = null;
    Map<String, Object> tr = (Map<String, Object>) r.get("trigger");
    if (tr != null) {
      trigger = new RegionTrigger(
          parseEnum(tr, "matchType", TriggerMatchType.class, TriggerMatchType.HEADER_SNIFF),
          tr.get("minMatchCount") instanceof Number ? ((Number) tr.get("minMatchCount")).intValue() : 1);
    }

    Map<String, Object> stratMap = (Map<String, Object>) r.get("strategy");
    RegionStrategy strategy = parseStrategy(stratMap, type);

    return new RegionDef(name, type, bindTo, trigger, strategy);
  }

  @SuppressWarnings("unchecked")
  private RegionStrategy parseStrategy(Map<String, Object> stratMap, RegionType type) {
    if (stratMap == null) return null;

    if (type == RegionType.KEY_VALUE) {
      Map<String, List<String>> labelAliases = new LinkedHashMap<>();
      Map<String, Object> la = (Map<String, Object>) stratMap.get("labelAliases");
      if (la != null) {
        la.forEach((k, v) -> labelAliases.put(k, (List<String>) v));
      }
      return new KvStrategy(
          parseEnum(stratMap, "valuePosition", KvValuePosition.class, KvValuePosition.RIGHT),
          labelAliases,
          stratMap.get("maxBlankRows") instanceof Number ? ((Number) stratMap.get("maxBlankRows")).intValue() : 3);
    } else if (type == RegionType.TABLE) {
      Map<String, List<String>> headerAliases = new LinkedHashMap<>();
      Map<String, Object> ha = (Map<String, Object>) stratMap.get("headerAliases");
      if (ha != null) {
        ha.forEach((k, v) -> headerAliases.put(k, (List<String>) v));
      }
      DataEndRule dataEnd = null;
      Map<String, Object> de = (Map<String, Object>) stratMap.get("dataEnd");
      if (de != null) {
        dataEnd = new DataEndRule(
            (List<String>) de.getOrDefault("markers", List.of()),
            de.get("blankRowCount") instanceof Number ? ((Number) de.get("blankRowCount")).intValue() : 0);
      }
      return new TableStrategy(
          stratMap.get("headerRows") instanceof Number ? ((Number) stratMap.get("headerRows")).intValue() : 1,
          stratMap.get("headerNameRow") instanceof Number ? ((Number) stratMap.get("headerNameRow")).intValue() : 0,
          parseEnum(stratMap, "matchBy", TableMatchBy.class, TableMatchBy.HEADER_NAME),
          headerAliases,
          parseEnum(stratMap, "headerMatching", HeaderMatching.class, HeaderMatching.STRICT),
          stratMap.get("maxRows") instanceof Number ? ((Number) stratMap.get("maxRows")).intValue() : 0,
          dataEnd);
    }
    return null;
  }

  private <E extends Enum<E>> E parseEnum(Map<String, Object> map, String key, Class<E> enumClass, E defaultVal) {
    Object val = map.get(key);
    if (val == null) return defaultVal;
    try {
      return Enum.valueOf(enumClass, val.toString());
    } catch (Exception e) {
      return defaultVal;
    }
  }

  private String generateId() {
    return "01HW-" + System.currentTimeMillis();
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test -Dtest=YamlConfigLoaderTest`
Expected: BUILD SUCCESS, 5 tests pass

- [ ] **Step 5: 提交**

```powershell
$msgPath = Join-Path $env:TEMP "commit-msg.txt"
@"
feat(file-infrastructure): complete YamlConfigLoader with full YAML to TemplateConfig mapping

- Parse canonicalModel (properties + tables with fields)
- Parse validationRules (field, scope, expr, message, type)
- Parse derivationRules
- Parse splitConfig (keys, splitKey, onMiss, maxRowsPerSubTask)
- Parse sourceTemplates (id, identifyMode, fingerprint, regions)
- Parse regions (name, type, bindTo, trigger, strategy)
- Parse KvStrategy and TableStrategy (including headerNameRow, headerAliases, dataEnd)
- 5 test cases covering all config sections
"@ | Out-File -FilePath $msgPath -Encoding utf8
git add file-service/file-infrastructure/src/
git commit --no-gpg-sign -F $msgPath
Remove-Item $msgPath
```

---

### Task 4: ParseFlowIntegrationTest 端到端集成测试

**Files:**

- Create: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ParseFlowIntegrationTest.java`

- [ ] **Step 1: 写端到端测试**

创建 `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ParseFlowIntegrationTest.java`：

```java
package com.example.file.infrastructure;

import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.service.CanonicalModelBuilder;
import com.example.file.domain.service.DataValidator;
import com.example.file.domain.service.KeyValueRegionParser;
import com.example.file.domain.service.ParseContext;
import com.example.file.domain.service.RegionStateMachine;
import com.example.file.domain.service.TableRegionParser;
import com.example.file.domain.service.TaskSplitter;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ParseFlowIntegrationTest {

  private static final String EXCEL_PATH =
      Path.of("docs", "excel", "示例表单.xlsx").toString();

  @Test
  void 完整解析流程_excel到规范数据() throws Exception {
    List<RegionDef> regions = buildRegionDefs();
    ExcelParser excelParser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
        RegionType.KEY_VALUE, new KeyValueRegionParser(),
        RegionType.TABLE, new TableRegionParser()));

    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      RawRowStream stream = excelParser.openStream(is);
      ParseContext ctx = new ParseContext(regions);
      List<RegionParseResult> results = stateMachine.drive(stream, regions, ctx);

      CanonicalModelBuilder builder = new CanonicalModelBuilder();
      CanonicalData data = builder.build(results, regions);

      // 验证 properties
      assertThat(data.properties())
          .containsEntry("planNo", "0200010001")
          .containsEntry("planName", "企业计划A")
          .containsEntry("customerNo", "000234")
          .containsEntry("customerName", "客户A");

      // 验证 tables（字段名是标准名，非 Excel 列代码）
      assertThat(data.tables()).containsKey("employees");
      assertThat(data.tables().get("employees")).hasSize(3);
      assertThat(data.tables().get("employees").get(0))
          .containsEntry("seq", "1")
          .containsEntry("name", "张内Aa01")
          .containsEntry("idType", "身份证");
    }
  }

  @Test
  void 校验流程_按标准字段名校验() throws Exception {
    List<RegionDef> regions = buildRegionDefs();
    ExcelParser excelParser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
        RegionType.KEY_VALUE, new KeyValueRegionParser(),
        RegionType.TABLE, new TableRegionParser()));

    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      RawRowStream stream = excelParser.openStream(is);
      List<RegionParseResult> results = stateMachine.drive(stream, regions, new ParseContext(regions));
      CanonicalData data = new CanonicalModelBuilder().build(results, regions);

      // 校验规则用 expr 表达式 + 标准字段名
      List<ValidationRule> rules = List.of(
          new ValidationRule("idNo", ValidationScope.ROW, "idNo != null", "证件编号不能为空", FieldType.STRING),
          new ValidationRule("name", ValidationScope.ROW, "name != null", "姓名不能为空", FieldType.STRING));

      // 简单 ExpressionEvaluator 实现
      ExpressionEvaluator evaluator = (expr, ctx) -> {
        if (expr == null) return true;
        if (expr.endsWith("!= null")) {
          String field = expr.substring(0, expr.indexOf("!=")).trim();
          return ctx.containsKey(field) && ctx.get(field) != null;
        }
        return true;
      };

      // 对每行数据校验
      DataValidator validator = new DataValidator();
      for (Map<String, Object> row : data.tables().get("employees")) {
        ValidationResult result = validator.validate(row, rules, ErrorPolicy.COLLECT_ALL, evaluator);
        assertThat(result.isValid()).isTrue();
      }
    }
  }

  @Test
  void 拆分流程_按customerNo拆分() throws Exception {
    List<RegionDef> regions = buildRegionDefs();
    ExcelParser excelParser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
        RegionType.KEY_VALUE, new KeyValueRegionParser(),
        RegionType.TABLE, new TableRegionParser()));

    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      RawRowStream stream = excelParser.openStream(is);
      List<RegionParseResult> results = stateMachine.drive(stream, regions, new ParseContext(regions));
      CanonicalData data = new CanonicalModelBuilder().build(results, regions);

      // 转换为 Map<String, Object> 供 TaskSplitter 使用
      Map<String, Object> dataMap = new LinkedHashMap<>();
      dataMap.putAll(data.properties());
      dataMap.putAll(data.tables());

      // 拆分配置：sourcePath = "employees.customerNo"
      SplitConfig splitConfig = new SplitConfig(
          List.of("customerNo"),
          new SplitKeyDef("customerNo", "employees.customerNo", SplitKeyType.FIELD_VALUE),
          SplitMissPolicy.ERROR, null, null, false, 1000);

      TaskSplitter splitter = new TaskSplitter();
      List<SplitUnit> subTasks = splitter.split(dataMap, splitConfig);

      // 所有数据行的 customerNo 相同（000234），拆分为 1 个子任务
      assertThat(subTasks).hasSize(1);
      assertThat(subTasks.get(0).splitKey()).isEqualTo("000234");
    }
  }

  private List<RegionDef> buildRegionDefs() {
    return List.of(
        new RegionDef("basic_info", RegionType.KEY_VALUE, "properties",
            new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
            new KvStrategy(KvValuePosition.RIGHT,
                Map.of(
                    "planNo", List.of("企业计划编号："),
                    "planName", List.of("企业计划名称："),
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
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test -Dtest=ParseFlowIntegrationTest`
Expected: BUILD SUCCESS, 3 tests pass

**注意**：如果测试失败，检查：

1. `RegionStateMachine.shouldEnterRegion`：第一个区域无 trigger 时立即进入
2. `ParseContext.isNextRegionTrigger`：非空行匹配 count >= minMatchCount
3. `KeyValueRegionParser.matchLabels`：labelAliases 中的值与 Excel 中的值完全匹配（包括"："）
4. `TableRegionParser`：headerNameRow=1 取 R5（0-based index 0）

- [ ] **Step 3: 运行全部测试**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-infrastructure test`
Expected: BUILD SUCCESS, 13 tests pass（ExcelParserImplTest 5 + YamlConfigLoaderTest 5 + ParseFlowIntegrationTest 3）

- [ ] **Step 4: 提交**

```powershell
$msgPath = Join-Path $env:TEMP "commit-msg.txt"
@"
test(file-infrastructure): add end-to-end integration test for parse flow

ParseFlowIntegrationTest:
- 完整解析流程: Excel → RegionStateMachine → CanonicalModelBuilder
- 校验流程: DataValidator with ExpressionEvaluator (expr-based rules)
- 拆分流程: TaskSplitter by customerNo (sourcePath: employees.customerNo)

Uses domain layer RegionStateMachine (not ExcelParserImpl.parse) for architecture correctness.
"@ | Out-File -FilePath $msgPath -Encoding utf8
git add file-service/file-infrastructure/src/
git commit --no-gpg-sign -F $msgPath
Remove-Item $msgPath
```

---

## 最终验证

- [ ] **全量编译 + 测试**

Run: `mvn "-Dmaven.legacyLocalRepo=true" -pl file-service/file-domain,file-service/file-infrastructure test`
Expected: BUILD SUCCESS, domain 30 tests + infrastructure 13 tests = 43 tests pass
