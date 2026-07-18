# 基于真实 Excel 的解析测试与遗留项完善设计

**日期**: 2026-07-19
**分支**: `feature/file-service-parse-engine`
**输入文件**: `docs/excel/示例表单.xlsx`

## 1. 背景与目标

file-service 解析引擎的 Phase B-I 已完成，7 层模块全部编译通过，28 个 domain 层单元测试全绿。但存在以下遗留项：

1. **ExcelParserImpl** 的 `parse` 方法是简化实现，无法处理真实 Excel 的复杂场景
2. **YamlConfigLoader** 是骨架实现，无法完整解析 YAML → TemplateConfig
3. 缺乏基于真实 Excel 文件的端到端测试验证

本设计的目标：
- 基于 `docs/excel/示例表单.xlsx` 创建测试案例，验证解析、校验等完整流程
- 完善 `ExcelParserImpl` 和 `YamlConfigLoader` 两个遗留项
- 采用 TDD 方式，分三层独立测试

## 2. 示例 Excel 结构分析

`docs/excel/示例表单.xlsx` 的 Sheet1（15 行 × 42 列）包含 4 个区域：

### 区域 1: KV 基本信息（R1-R2）

每行 2 组 label-value（label 在 col 1/3，value 在 col 2/4）：

```
R1: 企业计划编号： | 0200010001 | 企业计划名称： | 企业计划A
R2: 企业客户号：   | 000234     | 企业客户名称： | 客户A
```

### 区域 2: 说明行（R3-R4，跳过）

```
R3: (空行)
R4: 请在填写表单之前仔细阅读填表说明，以免造成提交出错！
```

### 区域 3: 表格区域（R5-R11）

3 行表头 + 3 行数据 + 1 行结束标记：

```
R5: XH | XM | ZJLX | ZJHM | ... (42 列代码行)
R6: 基本信息 | ... | 个人税收居民身份信息... (合并单元格分组标题)
R7: 序号* | 个人姓名* | 证件类型* | ... (42 列中文表头，实际列名)
R8: 1 | 张内Aa01 | 身份证 | 999000198608060000 | ... (数据行 1)
R9: 2 | 张内Aa02 | 护照   | 999000198608060000 | ... (数据行 2)
R10: 3 | 张内Aa03 | 身份证 | 999000198608060000 | ... (数据行 3)
R11: 结束 | 如果在提交表单时本行以上部分存在空行，请将空行删除。
```

### 区域 4: 填表人信息（R12，KV）

```
R12: 填表人: | 张三 | 复核人： | 李四
```

### 区域 5: 填表说明（R13-R15，跳过）

```
R13: 填表说明（请仔细阅读）：
R14: 1 | 说明内容1
R15: 2 | 说明内容2
```

## 3. ExcelParserImpl 完善设计

### 3.1 问题分析

当前 `ExcelParserImpl` 的 3 个不足：

| 问题 | 当前行为 | 期望行为 |
|------|----------|----------|
| KV 每行多组 label-value | 只读 col1=key, col2=value | 扫描所有列，按 label-value 对交替读取 |
| 表格多行表头 | 取第一行作为 headers | 取第 headerRows 行作为 headers（最后一行） |
| 结束标记行 | 只靠空行退出 | 检查 DataEndRule.markers 匹配 |

### 3.2 完善方案（不改领域模型）

`KvStrategy` 和 `TableStrategy` 已有足够字段，只需改 `ExcelParserImpl`：

#### KV 多组解析

`parseKvRegion` 方法改为：扫描每行所有列，按列交替读取 label-value 对。

```
对于每行:
  col = 1
  while col <= maxCol:
    label = cells[col]
    value = cells[col+1]
    if label 非空:
      data.put(label清理后缀, value)
    col += 2
```

label 清理：去掉末尾的 `：`、`:`、空格。

#### 多行表头取最后一行

`parseTableRegion` 方法改为：跳过 `headerRows - 1` 行，取第 `headerRows` 行作为 headers。

```
headerStart = triggerRow + 1
headerRow = rows[headerStart + headerRows - 1]  // 最后一行表头
headers = headerRow 的所有单元格值
dataStart = headerStart + headerRows
```

#### 结束标记检测

`parseTableRegion` 方法增加：检查每行第一列是否匹配 `DataEndRule.markers`。

```
对于每个数据行:
  if dataEnd.markers 非空 and cells[1] in markers:
    break  // 结束数据读取
```

## 4. YamlConfigLoader 完善设计

### 4.1 YAML 配置结构

```yaml
bizType: ENTERPRISE_PLAN
version: "1.0"
errorPolicy: COLLECT_ALL

canonicalModel:
  properties:
    - { name: planNo, type: STRING, required: true }
    - { name: planName, type: STRING, required: true }
    - { name: customerNo, type: STRING, required: true }
    - { name: customerName, type: STRING, required: true }
    - { name: filler, type: STRING }
    - { name: reviewer, type: STRING }
  tables:
    - name: employees
      fields:
        - { name: seq, type: INTEGER, required: true }
        - { name: name, type: STRING, required: true }
        - { name: idType, type: STRING, required: true }
        - { name: idNo, type: STRING, required: true }
        # ... 其余字段省略

validationRules:
  - { field: idNo, type: NOT_NULL, message: "证件编号不能为空", scope: TABLE_ROW }
  - { field: name, type: NOT_NULL, message: "姓名不能为空", scope: TABLE_ROW }

derivationRules: []

splitConfig:
  keys:
    - { key: customerNo, type: FIELD }
  missPolicy: FAIL
  rowLimit: 1000

sourceTemplates:
  - id: STANDARD_TEMPLATE
    name: "企业计划标准模板"
    identifyMode: AUTO
    fingerprint: ["企业计划编号：", "企业客户号：", "序号*"]
    regions:
      - name: basic_info
        type: KEY_VALUE
        bindTo: properties
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 2 }
        strategy:
          valuePosition: RIGHT
          maxBlankRows: 2
      - name: employee_list
        type: TABLE
        bindTo: employees
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 5 }
        strategy:
          headerRows: 3
          dataEnd: { markers: ["结束"], blankRowCount: 1 }
      - name: filler_info
        type: KEY_VALUE
        bindTo: properties
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 1 }
        strategy:
          valuePosition: RIGHT
          maxBlankRows: 1
```

### 4.2 完善内容

`YamlConfigLoader.buildTemplateConfig` 方法完善：

1. 解析 `canonicalModel` → `CanonicalModelDef`（PropertyFieldDef 列表 + TableDef 列表）
2. 解析 `validationRules` → `List<ValidationRule>`
3. 解析 `derivationRules` → `List<DerivationRule>`
4. 解析 `splitConfig` → `SplitConfig`（含 SplitKeyDef 列表）
5. 解析 `sourceTemplates` → `List<SourceTemplateDef>`（含 RegionDef 列表，含 RegionTrigger + RegionStrategy）

## 5. 三层测试设计

### 5.1 ExcelParserImplTest（解析层单元测试）

**位置**: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/ExcelParserImplTest.java`

**测试数据**: `docs/excel/示例表单.xlsx`（通过相对路径读取）

| 用例 | 验证点 |
|------|--------|
| `openStream_读取所有行` | 15 行，R1 非空，R3 空行 |
| `parse_KV区域_多组label_value` | basic_info 解析出 4 个 KV |
| `parse_表格区域_多行表头` | headers 取 R7 中文表头，3 条数据行 |
| `parse_表格区域_结束标记` | 遇"结束"行停止 |
| `parse_KV区域2_填表人` | filler=张三, reviewer=李四 |

**配置构建**: Java 代码直接构建 `List<RegionDef>`

### 5.2 YamlConfigLoaderTest（ConfigLoader 单元测试）

**位置**: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/gateway/YamlConfigLoaderTest.java`

**测试数据**: 内联 YAML 字符串

| 用例 | 验证点 |
|------|--------|
| `loadFromYaml_基础字段` | bizType、version、errorPolicy |
| `loadFromYaml_规范模型` | properties 6 个，tables 1 个 |
| `loadFromYaml_校验规则` | validationRules 2 条 |
| `loadFromYaml_拆分配置` | keys 1 个，missPolicy=FAIL |
| `loadFromYaml_源模板` | sourceTemplates 1 个，regions 3 个 |

### 5.3 ParseFlowIntegrationTest（端到端集成测试）

**位置**: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/ParseFlowIntegrationTest.java`

**测试流程**: Excel → ExcelParser.parse → CanonicalModelBuilder → DataValidator → TaskSplitter

| 用例 | 验证点 |
|------|--------|
| `完整解析流程` | properties 6 个 KV，tables.employees 3 行 |
| `校验流程` | 3 条数据行校验结果 |
| `拆分流程` | 按 customerNo 拆分 1 个子任务 |

**配置构建**: Java 代码构建 `TemplateConfig`

**依赖**: domain 层 `CanonicalModelBuilder`、`DataValidator`、`TaskSplitter`；infrastructure 层 `ExcelParserImpl`

## 6. 范围边界

### 在范围内
- 完善 `ExcelParserImpl` 的 3 个场景（KV 多组、多行表头、结束标记）
- 完善 `YamlConfigLoader` 的 YAML → TemplateConfig 完整映射
- 3 个测试类（15 个测试用例）

### 不在范围内
- 不修改 domain 层的值对象和领域服务（已有字段足够）
- 不做 Spring Boot 集成测试（不启动 ApplicationContext）
- 不做数据库集成测试（不涉及 Repository）
- 不完善 ParsedDataAdapter/TemplateConfigAdapter（不在本次范围）
