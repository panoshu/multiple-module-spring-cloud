# Apache Fesod (Incubating) 使用说明文档

> **F**ast **E**asy **S**preadsheet and **O**ther **D**ocuments
> 官方网站：<https://fesod.apache.org/>
> 核心理念： **Fast. Easy. Done.** —— 无需担心大文件引发 OOM 即可处理电子表格。

---

## 一、项目简介

**Apache Fesod (Incubating)** 是一个专注于 **高性能**与 **内存效率**的 Java 电子表格读写库，专为简化开发、确保可靠性而设计。

### 项目演进历史

| 时间    | 阶段                      | 说明                                                                  |
|---------|---------------------------|-----------------------------------------------------------------------|
| 早期    | EasyExcel                 | 阿里开源的 Excel 处理库，作者 @Jiaju Zhuang                           |
| 2023 年 | FastExcel                 | 原作者带领团队启动，对底层架构进行彻底重构                            |
| 2025 年 | Apache Fesod (Incubating) | 项目捐赠给 Apache 软件基金会，进入孵化器，**正式更名为 Apache Fesod** |

> **名称由来**：Fesod（发音 `/ˈfɛsɒd/`）是 "**F**ast **E**asy **S**preadsheet and **O**ther **D**ocuments"
> 的缩写，体现了项目的起源、背景与愿景。

### 核心能力

| 痛点       | 传统方案（如 POI DOM）       | Fesod 方案                         |
|------------|------------------------------|------------------------------------|
| 内存占用   | 100MB 文件可消耗 1GB+ 内存   | 默认智能缓存，可控制在数百 MB 以内 |
| 大文件 OOM | 百万行频繁 Full GC / OOM     | SAX 流式解析 + 磁盘缓存自适应切换  |
| 并发能力   | 多任务易耗尽堆内存           | 每任务可独立配置缓存阈值           |
| API 易用性 | 需手动分页/分批/临时文件管理 | 监听器 + 流式 API，零样板代码      |

### 1.1 版本兼容性

| 版本             | 发布日期       | JDK 支持范围                                  | 备注 |
|------------------|----------------|-----------------------------------------------|------|
| 2.0.2-incubating | JDK8 - JDK25   | Apache Incubator release (最新版本，推荐使用) |
| 2.0.1-incubating | JDK8 - JDK25   | Apache Incubator release                      |
| 2.0.0-incubating | JDK8 - JDK25   | NA(not available)                             |
| 1.3.0            | JDK 8 - JDK 25 | Non-Apache release                            |
| 1.2.0            | JDK 8 - JDK 21 | Non-Apache release                            |
| 1.1.0            | JDK 8 - JDK 21 | Non-Apache release                            |
| 1.0.0            | JDK 8 - JDK 21 | Non-Apache release                            |

> **重要说明**：
> - 当前 1.x 系列版本均为 **进入 Apache 孵化器之前**发布的版本（Non-Apache Releases），仍以 FastExcel 名称发布。
> - 目前最新的版本为 `2.0.2-incubating` 。
> - 强烈建议使用最新版本，以获取性能优化、Bug 修复和新特性。

### 1.2 Maven 依赖

#### 方式一：新坐标（ 推荐 ）

```xml
<dependency>
    <groupId>org.apache.fesod</groupId>
    <artifactId>fesod</artifactId>
    <version>${fesod.version}</version>
</dependency>
```

> **坐标说明**：
> - 项目已从 **FastExcel** 更名为 **Apache Fesod**，Java 包路径与入口 API 已迁移到 `org.apache.fesod.sheet.FesodSheet`。
> - 官方文档的 Spring 集成示例已使用新坐标 `org.apache.fesod:fesod`。
> - **新项目建议直接使用新坐标** `org.apache.fesod:fesod`；
> - 底层仍以 Apache POI 作为解析基础包，若项目中已引入 POI 相关组件，需要手动排除以避免版本冲突。

### 1.3 Gradle 依赖

```groovy
dependencies {
    // 新坐标（推荐）
    implementation 'org.apache.fesod:fesod:${fesodVersion}'
}
```

### 1.4 核心 API 入口

```java
org.apache.fesod.sheet.FesodSheet
```

`FesodSheet` 是统一的门面类，所有读写、填充操作均通过它的链式 API 完成。

---

## 二、流式读取（Streaming Read）

### 2.1 设计原理

Fesod 读取的核心是 **事件驱动的 SAX 解析 + 监听器回调**模式，避免将整个工作簿一次性载入内存：

```
ExcelReaderBuilder ──build()──> ExcelReader
                                       │
                                       ▼
                              XlsxSaxAnalyser
                                       │ (逐行触发)
                                       ▼
                              ReadListener#invoke(data, ctx)
                                       │ (全部解析完)
                                       ▼
                              ReadListener#doAfterAllAnalysed(ctx)
```

#### 三层内存优化架构

1. **智能缓存选择器（ReadCacheSelector）**
  - 接口：`org.apache.fesod.sheet.read.readcache.ReadCacheSelector`
  - 默认实现：`SimpleReadCacheSelector`
  - 默认阈值： **5MB**（共享字符串表小于该值走内存，大于则切换磁盘缓存）

2. **流式 SAX 解析引擎（XlsxSaxAnalyser）**
  - 基于 `SAXParser` 逐行解析，不构建完整 DOM 树
  - 通过 `XlsxRowHandler` 把每一行作为事件抛给监听器

3. **自适应存储策略（Ehcache）**
  - 每 100 条数据一个批次写入磁盘缓存
  - 内存中保留 LRU 最近访问数据
  - 大数据量自动溢出到文件存储

### 2.2 POJO 与注解

定义与电子表格结构对应的 POJO 类：

```java
@Getter
@Setter
@EqualsAndHashCode
public class DemoData {
    private String string;
    private Date date;
    private Double doubleData;
}
```

常用注解：

| 注解                                             | 作用         |
|--------------------------------------------------|--------------|
| `@ExcelProperty(index = 2)`                      | 按列下标读取 |
| `@ExcelProperty("String Title")`                 | 按列名读取   |
| `@ExcelIgnore`                                   | 忽略字段     |
| `@DateTimeFormat("yyyyMMddHHmmss")`              | 日期格式化   |
| `@NumberFormat("#.##%")`                         | 数字格式化   |
| `@ExcelProperty(converter = XxxConverter.class)` | 自定义转换器 |

### 2.3 三种监听器写法

> **重要约束**：监听器 **不能**由 Spring 容器管理，每次读取都需重新实例化（监听器是有状态的）。

#### 2.3.1 Lambda 表达式（推荐用于简单场景）

```java
@Test
public void simpleRead() {
    String fileName = "path/to/demo.xlsx";
    FesodSheet.read(fileName, DemoData.class, new PageReadListener<>(dataList -> {
        for (DemoData demoData : dataList) {
            log.info("Read one record: {}", JSON.toJSONString(demoData));
        }
    })).sheet().doRead();
}
```

`PageReadListener` 默认每 100 条触发一次回调，适合分批入库、批量处理。

#### 2.3.2 匿名内部类

```java
@Test
public void simpleRead() {
    String fileName = "path/to/demo.xlsx";
    FesodSheet.read(fileName, DemoData.class, new ReadListener<DemoData>() {
        @Override
        public void invoke(DemoData data, AnalysisContext context) {
            log.info("Read one record: {}", JSON.toJSONString(data));
        }
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            log.info("All data reading completed!");
        }
    }).sheet().doRead();
}
```

#### 2.3.3 自定义监听器类

```java
@Slf4j
public class DemoDataListener implements ReadListener<DemoData> {
    @Override
    public void invoke(DemoData data, AnalysisContext context) {
        log.info("Read one record: {}", JSON.toJSONString(data));
    }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("All data reading completed!");
    }
}

// 使用
FesodSheet.read(fileName, DemoData.class, new DemoDataListener())
        .sheet()
        .doRead();
```

### 2.4 同步读取（小数据量场景）

对于小文件，可使用 `doReadSync()` 直接将结果载入内存 `List`，无需监听器。

#### 2.4.1 读取为 POJO List

```java
@Test
public void synchronousReadToObjectList() {
    String fileName = "path/to/demo.xlsx";
    List<DemoData> list = FesodSheet.read(fileName)
            .head(DemoData.class)
            .sheet()
            .doReadSync();
    list.forEach(d -> log.info("Read data: {}", JSON.toJSONString(d)));
}
```

#### 2.4.2 读取为 Map List（无 POJO）

```java
@Test
public void synchronousReadToMapList() {
    String fileName = "path/to/demo.xlsx";
    List<Map<Integer, String>> list = FesodSheet.read(fileName)
            .sheet()
            .doReadSync();
    // Map 的 key 是列下标，value 是单元格内容
    list.forEach(d -> log.info("Read data: {}", JSON.toJSONString(d)));
}
```

### 2.5 无 POJO 读取（Map 模式）

完全跳过 POJO 定义，使用 `Map<Integer, String>` 接收数据：

```java
@Slf4j
public class NoModelDataListener extends AnalysisEventListener<Map<Integer, String>> {
    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        log.info("Read one record: {}", JSON.toJSONString(data));
    }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("All data reading completed!");
    }
}

// 使用
FesodSheet.read(fileName, new NoModelDataListener()).sheet().doRead();
```

### 2.6 多 Sheet 读取

#### 2.6.1 读取所有 Sheet

```java
@Test
public void readAllSheet() {
    String fileName = "path/to/demo.xlsx";
    FesodSheet.read(fileName, DemoData.class, new DemoDataListener())
            .doReadAll();
}
```

#### 2.6.2 读取指定 Sheet（按下标或名称）

```java
@Test
public void readSingleSheet() {
    String fileName = "path/to/demo.xlsx";
    try (ExcelReader excelReader = FesodSheet.read(fileName).build()) {
        // 按下标
        ReadSheet sheet1 = FesodSheet.readSheet(0)
                .head(DemoData.class)
                .registerReadListener(new DemoDataListener())
                .build();
        // 按名称
        ReadSheet sheet2 = FesodSheet.readSheet("Sheet2")
                .head(DemoData.class)
                .registerReadListener(new DemoDataListener())
                .build();
        excelReader.read(sheet1, sheet2);
    }
}
```

> **注意**：Excel 的 Sheet 名称上限为 31 个字符，按名称读取时使用文件中实际显示的名称。

### 2.7 表头处理

#### 2.7.1 读取表头信息

通过重写 `invokeHead` 获取表头：

```java
@Slf4j
public class DemoHeadDataListener extends AnalysisEventListener<DemoData> {
    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        log.info("Parsed header data: {}", JSON.toJSONString(headMap));
    }
    @Override
    public void invoke(DemoData data, AnalysisContext context) { }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
}
```

#### 2.7.2 多行表头

通过 `headRowNumber(int)` 设置表头行数（默认 1）：

```java
FesodSheet.read(fileName, DemoData.class, new DemoDataListener())
        .sheet()
        .headRowNumber(2)  // 2 行表头
        .doRead();
```

### 2.8 忽略隐藏 Sheet

支持忽略"普通隐藏（xlSheetHidden）"与"深度隐藏（xlSheetVeryHidden，需 VBA 设置）"两种状态：

```java
@Test
public void exceptionRead() {
    String fileName = "path/to/demo.xlsx";
    FesodSheet.read(fileName, DemoData.class, new DemoDataListener())
            .ignoreHiddenSheet(Boolean.TRUE)
            .sheet()
            .doRead();
}
```

### 2.9 列过滤（只读取特定列）

```java
@Test
public void readSpecificColumnsToMapList() {
    String fileName = "path/to/demo.xlsx";
    // 只读第 0 列和第 2 列
    List<Integer> targetColumns = Arrays.asList(0, 2);
    List<Map<Integer, String>> list = FesodSheet.read(fileName)
            .sheet()
            .includeColumnIndexes(targetColumns)
            .doReadSync();
    // 过滤后的列会**按顺序**重新映射：原第 0 列 → key 0，原第 2 列 → key 1
    for (Map<Integer, String> data : list) {
        String id = data.get(0);
        String age = data.get(1);
        log.info("ID: {}, Age: {}", id, age);
    }
}
```

### 2.10 CellData（保留公式与格式）

需要保留单元格原始信息（公式、样式）时使用 `CellData<T>`：

```java
@Getter
@Setter
@EqualsAndHashCode
public class CellDataReadDemoData {
    private CellData<String> string;
    private CellData<Date> date;
    private CellData<Double> doubleData;
    private CellData<String> formulaValue;
}

FesodSheet.read(fileName, CellDataReadDemoData.class, new DemoDataListener())
        .sheet()
        .doRead();
```

### 2.11 自定义转换器

```java
public class CustomStringStringConverter implements Converter<String> {
    @Override
    public Class<?> supportJavaTypeKey() { return String.class; }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() { return CellDataTypeEnum.STRING; }

    @Override
    public String convertToJavaData(ReadConverterContext<?> context) {
        return "Custom: " + context.getReadCellData().getStringValue();
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<String> context) {
        return new WriteCellData<>(context.getValue());
    }
}

// POJO 使用
public class ConverterData {
    @ExcelProperty(converter = CustomStringStringConverter.class)
    private String string;

    @DateTimeFormat("yyyyMMddHHmmss")
    private String date;

    @NumberFormat("#.##%")
    private String doubleData;
}

// 注册使用
FesodSheet.read(fileName, ConverterData.class, new DemoDataListener())
        .registerConverter(new CustomStringStringConverter())
        .sheet()
        .doRead();
```

### 2.12 格式化（@DateTimeFormat / @NumberFormat）

当 POJO 字段类型为 `String` 时，可通过注解把日期、数字单元格自动转换为格式化字符串：

```java
@Getter
@Setter
@EqualsAndHashCode
public class ConverterData {
    @ExcelProperty(value = "String Title", converter = CustomStringStringConverter.class)
    private String string;

    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @ExcelProperty("Date Title")
    private String date;

    @NumberFormat("#.##%")
    @ExcelProperty("DocumentNumber Title")
    private String doubleData;
}
```

转换行为示例：

| Excel 单元格值        | 注解                                                            | Java 字段值             |
|-----------------------|-----------------------------------------------------------------|-------------------------|
| `Hello`               | `@ExcelProperty(converter = CustomStringStringConverter.class)` | `"Custom: Hello"`       |
| `2025-01-01 12:30:00` | `@DateTimeFormat("yyyy-MM-dd HH:mm:ss")`                        | `"2025-01-01 12:30:00"` |
| `0.56`                | `@NumberFormat("#.##%")`                                        | `"56%"`                 |

#### 注解参数详解

**`@DateTimeFormat`**：日期单元格转 String

| 参数               | 默认值 | 说明                                        |
|--------------------|--------|---------------------------------------------|
| `value`            | 空     | 日期格式，遵循 `java.text.SimpleDateFormat` |
| `use1904windowing` | 自动   | Excel 文件使用 1904 日期系统时设为 `true`   |

**`@NumberFormat`**：数字单元格转 String

| 参数           | 默认值    | 说明                                     |
|----------------|-----------|------------------------------------------|
| `value`        | 空        | 数字格式，遵循 `java.text.DecimalFormat` |
| `roundingMode` | `HALF_UP` | 格式化时的舍入模式                       |

### 2.13 行数限制（numRows）

默认读取整表所有数据。通过 `numRows(int)` 限制只读取前 N 行， **N 包含表头行**；设为 `0` 表示不限制。

#### 2.13.1 所有 Sheet 限制

```java
@Test
public void allSheetRead() {
    // 只读取前 100 行
    FesodSheet.read(fileName, DemoData.class, new PageReadListener<DemoData>(dataList -> {
        for (DemoData demoData : dataList) {
            log.info("Read one record: {}", JSON.toJSONString(demoData));
        }
    })).numRows(100).sheet().doRead();
}
```

#### 2.13.2 单个 Sheet 限制

```java
@Test
public void singleSheetRead() {
    try (ExcelReader excelReader = FesodSheet.read(fileName, DemoData.class, new DemoDataListener()).build()) {
        ReadSheet readSheet = FesodSheet.readSheet(0).build();
        readSheet.setNumRows(100);  // 只读取前 100 行
        excelReader.read(readSheet);
    }
}
```

### 2.14 CSV 读取

Fesod 通过 [Apache Commons CSV](https://commons.apache.org/proper/commons-csv) 支持 CSV 读取，使用 `.csv()` 切换到 CSV
模式，再链式配置参数：

| 参数              | 默认值        | 说明                                                                              |
|-------------------|---------------|-----------------------------------------------------------------------------------|
| `delimiter`       | `,`（逗号）   | 字段分隔符，推荐使用 `CsvConstant` 常量（如 `CsvConstant.AT`、`CsvConstant.TAB`） |
| `quote`           | `"`（双引号） | 字段引号字符，推荐 `CsvConstant.DOUBLE_QUOTE`                                     |
| `recordSeparator` | `CRLF`        | 行分隔符，如 `CsvConstant.CRLF`（Windows）/ `CsvConstant.LF`（Unix）              |
| `nullString`      | `null`        | 表示 `null` 值的字符串（注意与空串 `""` 不同）                                    |
| `escape`          | `null`        | 转义字符，用于转义引号本身                                                        |

#### 2.14.1 基础用法

```java
@Test
public void delimiterDemo() {
    String csvFile = "path/to/your.csv";
    List<DemoData> dataList = FesodSheet.read(csvFile, DemoData.class, new DemoDataListener())
            .csv()
            .delimiter(CsvConstant.UNICODE_EMPTY)   // 使用 \u0000 作为分隔符
            .doReadSync();
}
```

#### 2.14.2 quote 配置（含 QuoteMode）

```java
@Test
public void quoteDemo() {
    String csvFile = "path/to/your.csv";
    List<DemoData> dataList = FesodSheet.read(csvFile, DemoData.class, new DemoDataListener())
            .csv()
            .quote(CsvConstant.DOUBLE_QUOTE, QuoteMode.MINIMAL)
            .doReadSync();
}
```

#### 2.14.3 recordSeparator / nullString / escape

```java
// 行分隔符（Linux）
FesodSheet.read(csvFile, DemoData.class, listener)
        .csv()
        .recordSeparator(CsvConstant.LF)
        .doReadSync();

// 把字符串 "N/A" 解析为 null
FesodSheet.read(csvFile, DemoData.class, listener)
        .csv()
        .nullString("N/A")
        .doReadSync();

// 使用反斜杠作为转义字符
FesodSheet.read(csvFile, DemoData.class, listener)
        .csv()
        .escape(CsvConstant.BACKSLASH)
        .doReadSync();
```

#### 2.14.4 直接使用 CSVFormat（不推荐，但兼容）

```java
@Test
public void csvFormatDemo() {
    CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setDelimiter(CsvConstant.AT)
            .build();
    String csvFile = "path/to/your.csv";
    try (ExcelReader excelReader = FesodSheet.read(csvFile, DemoData.class, new DemoDataListener()).build()) {
        ReadWorkbookHolder holder = excelReader.analysisContext().readWorkbookHolder();
        if (holder instanceof CsvReadWorkbookHolder csvHolder) {
            csvHolder.setCsvFormat(csvFormat);
        }
        ReadSheet readSheet = FesodSheet.readSheet(0).build();
        excelReader.read(readSheet);
    }
}
```

### 2.15 额外信息读取（注释 / 超链接 / 合并单元格）

通过重写 `ReadListener#extra` 方法 + `.extraRead(CellExtraTypeEnum)` 注册要读取的额外信息类型：

| `CellExtraTypeEnum` | 含义           |
|---------------------|----------------|
| `COMMENT`           | 单元格注释     |
| `HYPERLINK`         | 超链接         |
| `MERGE`             | 合并单元格范围 |

#### 2.15.1 读取注释

```java
@Slf4j
public class DemoCommentExtraListener implements ReadListener<DemoData> {
    @Override
    public void invoke(DemoData data, AnalysisContext context) { }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
    @Override
    public void extra(CellExtra extra, AnalysisContext context) {
        log.info("Read extra information: {}", JSON.toJSONString(extra));
        if (CellExtraTypeEnum.COMMENT == extra.getType()) {
            log.info("Comment information: {}", extra.getText());
        }
    }
}

@Test
public void extraRead() {
    String fileName = "path/to/demo.xlsx";
    FesodSheet.read(fileName, DemoData.class, new DemoCommentExtraListener())
            .extraRead(CellExtraTypeEnum.COMMENT)
            .sheet()
            .doRead();
}
```

#### 2.15.2 读取超链接

```java
@Slf4j
public class DemoHyperLinkExtraListener implements ReadListener<DemoData> {
    @Override
    public void invoke(DemoData data, AnalysisContext context) { }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
    @Override
    public void extra(CellExtra extra, AnalysisContext context) {
        if (CellExtraTypeEnum.HYPERLINK == extra.getType()) {
            log.info("Hyperlink information: {}", extra.getText());
        }
    }
}

FesodSheet.read(fileName, DemoData.class, new DemoHyperLinkExtraListener())
        .extraRead(CellExtraTypeEnum.HYPERLINK)
        .sheet()
        .doRead();
```

#### 2.15.3 读取合并单元格范围

```java
@Slf4j
public class DemoMergeExtraListener implements ReadListener<DemoData> {
    @Override
    public void invoke(DemoData data, AnalysisContext context) { }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
    @Override
    public void extra(CellExtra extra, AnalysisContext context) {
        if (CellExtraTypeEnum.MERGE == extra.getType()) {
            log.info("Merged cell range: {} - {}",
                    extra.getFirstRowIndex(), extra.getLastRowIndex());
        }
    }
}

FesodSheet.read(fileName, DemoData.class, new DemoMergeExtraListener())
        .extraRead(CellExtraTypeEnum.MERGE)
        .sheet()
        .doRead();
```

### 2.16 异常处理

通过重写 `ReadListener#onException` 捕获解析过程中的异常。常见异常类型 `ExcelDataConvertException` 携带行号、列号、单元格数据等上下文：

```java
@Slf4j
public class DemoExceptionListener extends AnalysisEventListener<ExceptionDemoData> {
    @Override
    public void onException(Exception exception, AnalysisContext context) {
        log.error("Failed: {}", exception.getMessage());
        if (exception instanceof ExcelDataConvertException ex) {
            log.error("Row {}, Column {} exception, data: {}",
                    ex.getRowIndex(), ex.getColumnIndex(), ex.getCellData());
        }
    }
    @Override
    public void invoke(ExceptionDemoData data, AnalysisContext context) { }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
}

// 使用
FesodSheet.read(fileName, ExceptionDemoData.class, new DemoExceptionListener())
        .sheet()
        .doRead();
```

> 若累计错误数过多需主动中止解析，可在 `onException` 中抛出 `ExcelAnalysisStopException`。

### 2.17 Spring 集成（文件上传场景）

通过 Spring 的 `MultipartFile` 接收前端上传的 Excel 文件，直接通过 `InputStream` 交给 Fesod 解析。

#### 2.17.1 POJO 与监听器

```java
@Getter
@Setter
@ToString
public class UploadData {
    private String string;
    private Date date;
    private Double doubleData;
}

@Slf4j
public class UploadDataListener extends AnalysisEventListener<UploadData> {
    private final List<UploadData> list = new ArrayList<>();
    @Override
    public void invoke(UploadData data, AnalysisContext context) {
        log.info("Read one record: {}", JSON.toJSONString(data));
        list.add(data);
    }
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("All data reading completed!");
        // 这里可执行数据库持久化等操作
    }
}
```

#### 2.17.2 Controller

```java
@RestController
@RequestMapping("/excel")
public class ExcelController {

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload!");
        }
        try {
            FesodSheet.read(file.getInputStream(), UploadData.class, new UploadDataListener())
                    .sheet()
                    .doRead();
            return ResponseEntity.ok("File uploaded and processed successfully!");
        } catch (IOException e) {
            log.error("File processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File processing failed");
        }
    }
}
```

> **注意**：监听器 **不能**作为 Spring Bean 注入，每次请求都需要 `new` 一个新实例。若需要在监听器中调用 Spring Bean（如
> Service），可通过构造函数传入。

### 2.18 大文件内存优化（核心）

#### 2.18.1 缓存策略选择

| 场景           | 文件大小 | 配置策略                          |
|----------------|----------|-----------------------------------|
| 小文件高性能   | < 5MB    | 强制内存缓存 `MapCache`           |
| 中等文件平衡   | 5 - 50MB | 默认智能选择（不配置）            |
| 大文件内存控制 | > 50MB   | `SimpleReadCacheSelector(20, 90)` |
| 超大文件       | > 500MB  | 自定义阈值 + `numRows` 分页       |

```java
// 场景1：小文件高性能模式（<5MB）
FesodSheet.read("small_file.xlsx", DataModel.class, listener)
        .readCache(new MapCache())   // 强制内存缓存，提升约 30% 速度
        .sheet()
        .doRead();

// 场景2：中等文件平衡模式（5-50MB），使用默认智能缓存
FesodSheet.read("medium_file.xlsx", DataModel.class, listener)
        .sheet()
        .doRead();

// 场景3：大文件内存控制模式（>50MB）
FesodSheet.read("large_file.xlsx", DataModel.class, listener)
        .readCacheSelector(new SimpleReadCacheSelector(20, 90))  // 20MB 阈值，90MB 缓存
        .sheet()
        .doRead();

// 场景4：超大规模文件（>500MB）
FesodSheet.read("huge_file.xlsx", DataModel.class, listener)
        .readCacheSelector(new SimpleReadCacheSelector(50, 200))  // 50MB 阈值，200MB 缓存
        .numRows(10000)   // 每次读取 10000 行
        .sheet()
        .doRead();
```

`SimpleReadCacheSelector(maxUseMapCacheSize, maxCacheActivateSize)` 参数说明：

- `maxUseMapCacheSize`：使用内存 `MapCache` 的阈值（单位 MB），共享字符串表小于该值走内存
- `maxCacheActivateSize`：切换到磁盘 `Ehcache` 后内存中活跃缓存的大小（单位 MB）

#### 2.18.2 高并发处理最佳实践

```java
ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder()
                .setNameFormat("excel-processor-%d")
                .build()
);

List<CompletableFuture<Void>> futures = files.stream()
        .map(file -> CompletableFuture.runAsync(() -> {
            FesodSheet.read(file, DataModel.class, new PageReadListener<>(
                    data -> processBatch(data),
                    1000  // 每批 1000 条
            ))
            .readCacheSelector(new SimpleReadCacheSelector(10, 50))  // 限制单任务内存
            .sheet()
            .doRead();
        }, executor))
        .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

#### 2.18.3 容错监听器

```java
public class ResilientReadListener implements ReadListener<DataModel> {
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final int maxErrors = 100;

    @Override
    public void invoke(DataModel data, AnalysisContext context) {
        try {
            processData(data);
        } catch (Exception e) {
            if (errorCount.incrementAndGet() > maxErrors) {
                throw new ExcelAnalysisStopException("Too many errors", e);
            }
            log.warn("Data processing error, skipping row", e);
        }
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        if (exception instanceof ExcelDataConvertException) {
            log.error("Data conversion error at row {}",
                    context.readRowHolder().getRowIndex(), exception);
        } else {
            throw new RuntimeException("Fatal error in Excel processing", exception);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) { }
}
```

### 2.19 读取 API 速查表

| 方法                                                                                                    | 用途                                              |
|---------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `FesodSheet.read(fileName)`                                                                             | 构造读取 Builder（也可传 `InputStream`）          |
| `FesodSheet.readSheet(int index)` / `readSheet(String name)`                                            | 构造指定 Sheet 的 Builder                         |
| `.head(Class<?>)`                                                                                       | 指定 POJO 类                                      |
| `.registerReadListener(listener)`                                                                       | 注册监听器                                        |
| `.sheet()`                                                                                              | 选择默认 Sheet                                    |
| `.sheet(int index)`                                                                                     | 按下标选 Sheet                                    |
| `.sheet(String name)`                                                                                   | 按名称选 Sheet                                    |
| `.headRowNumber(int)`                                                                                   | 设置表头行数（默认 1）                            |
| `.ignoreHiddenSheet(boolean)`                                                                           | 是否忽略隐藏 Sheet（含 very hidden）              |
| `.includeColumnIndexes(List<Integer>)`                                                                  | 仅读取指定列                                      |
| `.registerConverter(Converter<?>)`                                                                      | 注册自定义转换器                                  |
| `.readCache(ReadCache)`                                                                                 | 强制指定缓存实现（如 `MapCache`）                 |
| `.readCacheSelector(ReadCacheSelector)`                                                                 | 指定缓存选择器（如 `SimpleReadCacheSelector`）    |
| `.numRows(int)`                                                                                         | 限制单次读取行数（含表头，0=不限）                |
| `.extraRead(CellExtraTypeEnum)`                                                                         | 注册要读取的额外信息（COMMENT/HYPERLINK/MERGE）   |
| `.csv()`                                                                                                | 切换到 CSV 读取模式                               |
| `.csv().delimiter(...)` / `.quote(...)` / `.recordSeparator(...)` / `.nullString(...)` / `.escape(...)` | CSV 模式下的参数配置                              |
| `.build()`                                                                                              | 构造 `ExcelReader`（用于多 Sheet 读取或精细控制） |
| `.doRead()`                                                                                             | 异步执行（监听器回调）                            |
| `.doReadSync()`                                                                                         | 同步执行，返回 `List`                             |
| `.doReadAll()`                                                                                          | 读取所有 Sheet                                    |
| `excelReader.read(ReadSheet...)`                                                                        | 读取指定的一个或多个 Sheet                        |
| `readSheet.setNumRows(int)`                                                                             | 单 Sheet 级别的行数限制                           |

---

## 三、基于 Excel 模板的填充写入（Fill）

### 3.1 设计原理

填充（Fill）是 Fesod 区别于普通写入的核心特性：基于 **预定义的 Excel 模板**，按占位符规则把对象 / Map / List
数据回填到模板中，保留模板的样式、合并单元格、公式等所有视觉特性。

#### 模板占位符语法

| 占位符                | 含义                           | 示例                             |
|-----------------------|--------------------------------|----------------------------------|
| `{fieldName}`         | 普通变量，从对象/Map 取值      | `{name}` → 取 `name` 字段        |
| `{.fieldName}`        | 列表变量，列表中每个对象的字段 | `{.name}` → 列表项的 `name` 字段 |
| `{prefix.fieldName}`  | 多列表场景，带前缀区分         | `{data1.name}`、`{data2.name}`   |
| `{.prefix.fieldName}` | 多列表场景，列表字段带前缀     | `{.data1.name}`                  |

> 占位符可在模板的任意单元格中使用；列表占位符所在的行会被作为"模板行"，每条数据复制一行。

### 3.2 简单填充（Simple Fill）

#### POJO 类

```java
@Getter
@Setter
@EqualsAndHashCode
public class FillData {
    private String name;
    private double number;
    private Date date;
}
```

#### 模板示例（simple.xlsx）

| name   | number   | date   |
|--------|----------|--------|
| {name} | {number} | {date} |

#### 代码：对象方式 & Map 方式

```java
@Test
public void simpleFill() {
    String templateFileName = "path/to/simple.xlsx";

    // 方式1：基于对象填充
    FillData fillData = new FillData();
    fillData.setName("张三");
    fillData.setNumber(5.2);
    FesodSheet.write("simpleFill.xlsx")
            .withTemplate(templateFileName)
            .sheet()
            .doFill(fillData);

    // 方式2：基于 Map 填充
    Map<String, Object> map = new HashMap<>();
    map.put("name", "张三");
    map.put("number", 5.2);
    FesodSheet.write("simpleFillMap.xlsx")
            .withTemplate(templateFileName)
            .sheet()
            .doFill(map);
}
```

### 3.3 列表填充（List Fill）

#### 模板示例（list.xlsx）

| name    | number    | date    |
|---------|-----------|---------|
| {.name} | {.number} | {.date} |

#### 代码：一次性填充 & 批量填充

```java
@Test
public void listFill() {
    String templateFileName = "path/to/list.xlsx";

    // 方式1：一次性填充所有数据
    FesodSheet.write("listFill.xlsx")
            .withTemplate(templateFileName)
            .sheet()
            .doFill(data());

    // 方式2：批量填充（适用于数据来源分批获取）
    try (ExcelWriter writer = FesodSheet.write("listFillBatch.xlsx")
            .withTemplate(templateFileName)
            .build()) {
        WriteSheet writeSheet = FesodSheet.writerSheet().build();
        writer.fill(data(), writeSheet);
        writer.fill(data(), writeSheet);
    }
}
```

> 使用 `try-with-resources` 包裹 `ExcelWriter` 可确保资源正确释放，并支持多次 `fill` 调用累加数据。

### 3.4 复杂填充（Complex Fill）

#### 模板示例（complex.xlsx）

```
日期：{date}                        总计：{total}
+--------+--------+--------+
| {.name} | {.number} | {.date} |
+--------+--------+--------+
```

#### 代码：列表 + 普通变量混填

```java
@Test
public void complexFill() {
    String templateFileName = "path/to/complex.xlsx";

    try (ExcelWriter writer = FesodSheet.write("complexFill.xlsx")
            .withTemplate(templateFileName)
            .build()) {
        WriteSheet writeSheet = FesodSheet.writerSheet().build();

        // 填充列表数据，开启 forceNewRow 强制每次新建行（推荐用于复杂模板）
        FillConfig config = FillConfig.builder()
                .forceNewRow(true)
                .build();
        writer.fill(data(), config, writeSheet);

        // 填充普通变量
        Map<String, Object> map = new HashMap<>();
        map.put("date", "2024年11月20日");
        map.put("total", 1000);
        writer.fill(map, writeSheet);
    }
}
```

#### FillConfig 关键参数

| 参数          | 类型                 | 说明                                                                   |
|---------------|----------------------|------------------------------------------------------------------------|
| `forceNewRow` | `boolean`            | 是否强制新建行。开启后每次填充列表项都会插入新行，避免覆盖后续模板内容 |
| `direction`   | `WriteDirectionEnum` | 填充方向：`VERTICAL`（默认，纵向）/ `HORIZONTAL`（横向）               |

### 3.5 大数据量复杂填充（Complex Fill with Table）

当列表数据量很大，且列表后还有统计行时，最佳实践是 **把列表放在模板最后一行**，后续统计信息用 `WriteTable` 写入：

```java
@Test
public void complexFillWithTable() {
    String templateFileName = "path/to/complexFillWithTable.xlsx";

    try (ExcelWriter writer = FesodSheet.write("complexFillWithTable.xlsx")
            .withTemplate(templateFileName)
            .build()) {
        WriteSheet writeSheet = FesodSheet.writerSheet().build();

        // 1. 填充列表数据
        writer.fill(data(), writeSheet);

        // 2. 填充普通变量
        Map<String, Object> map = new HashMap<>();
        map.put("date", "2024年11月20日");
        writer.fill(map, writeSheet);

        // 3. 用 WriteTable 写入统计行
        List<List<String>> totalList = new ArrayList<>();
        totalList.add(Arrays.asList(null, null, null, "统计: 1000"));
        writer.write(totalList, writeSheet);
    }
}
```

### 3.6 横向填充（Horizontal Fill）

适用于 **列数动态**的场景（列表项按列展开而非按行展开）：

```java
@Test
public void horizontalFill() {
    String templateFileName = "path/to/horizontal.xlsx";

    try (ExcelWriter writer = FesodSheet.write("horizontalFill.xlsx")
            .withTemplate(templateFileName)
            .build()) {
        WriteSheet writeSheet = FesodSheet.writerSheet().build();

        FillConfig config = FillConfig.builder()
                .direction(WriteDirectionEnum.HORIZONTAL)
                .build();
        writer.fill(data(), config, writeSheet);

        Map<String, Object> map = new HashMap<>();
        map.put("date", "2024年11月20日");
        writer.fill(map, writeSheet);
    }
}
```

### 3.7 多列表组合填充（Composite Fill）

需要在同一模板中填充 **多个不同的列表**时，使用 `FillWrapper` 给每个列表加前缀：

#### 模板示例（composite.xlsx）

```
日期：{date}
| data1.name | data1.number | data2.name | data2.number | data3.name | data3.number |
| {.data1.name} | {.data1.number} | {.data2.name} | {.data2.number} | {.data3.name} | {.data3.number} |
```

#### 代码

```java
@Test
public void compositeFill() {
    String templateFileName = "path/to/composite.xlsx";

    try (ExcelWriter writer = FesodSheet.write("compositeFill.xlsx")
            .withTemplate(templateFileName)
            .build()) {
        WriteSheet writeSheet = FesodSheet.writerSheet().build();

        // 使用 FillWrapper 给每个列表指定前缀
        writer.fill(new FillWrapper("data1", data()), writeSheet);
        writer.fill(new FillWrapper("data2", data()), writeSheet);
        writer.fill(new FillWrapper("data3", data()), writeSheet);

        // 填充普通变量
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date());
        writer.fill(map, writeSheet);
    }
}
```

> `FillWrapper` 的构造参数为 `(prefix, dataList)`，模板中需使用 `{prefix.fieldName}` / `{.prefix.fieldName}` 形式引用。

### 3.8 填充 API 速查表

| 方法                                                 | 用途                                   |
|------------------------------------------------------|----------------------------------------|
| `FesodSheet.write(fileName)`                         | 构造写入 Builder                       |
| `.withTemplate(templateFileName)`                    | 指定模板文件                           |
| `.withTemplate(InputStream)`                         | 以流方式指定模板                       |
| `.sheet()`                                           | 选择默认 Sheet                         |
| `.sheet(int index)`                                  | 按下标选 Sheet                         |
| `.sheet(String name)`                                | 按名称选 Sheet                         |
| `.doFill(obj)`                                       | 一次性填充对象/Map/List                |
| `.doFill(obj, FillConfig)`                           | 带配置填充                             |
| `.build()`                                           | 构造 `ExcelWriter`（用于批量多次填充） |
| `writer.fill(data, writeSheet)`                      | 单次填充                               |
| `writer.fill(data, FillConfig, writeSheet)`          | 带配置单次填充                         |
| `writer.write(List<List<?>>, writeSheet)`            | 追加普通写入（不带模板规则）           |
| `FillConfig.builder().forceNewRow(true).build()`     | 强制新建行                             |
| `FillConfig.builder().direction(HORIZONTAL).build()` | 横向填充                               |
| `new FillWrapper(prefix, list)`                      | 多列表前缀包装                         |

---

## 四、最佳实践总结

### 4.1 读取场景选型

| 数据量           | 推荐方案                             | 关键配置                                               |
|------------------|--------------------------------------|--------------------------------------------------------|
| < 1 万行         | 同步 `doReadSync()`                  | 无需特殊配置                                           |
| 1 万 - 10 万行   | 监听器 + `PageReadListener` 分批入库 | 默认缓存                                               |
| 10 万 - 100 万行 | 监听器 + 自定义 `ReadCacheSelector`  | `SimpleReadCacheSelector(20, 90)`                      |
| > 100 万行       | 监听器 + 自定义阈值 + `numRows` 分页 | `SimpleReadCacheSelector(50, 200)` + `.numRows(10000)` |

### 4.2 填充场景选型

| 业务场景                              | 推荐方案                                     |
|---------------------------------------|----------------------------------------------|
| 单条数据回填（如合同、证书）          | 简单填充 `doFill(obj)`                       |
| 列表数据回填（如明细表）              | 列表填充 `doFill(list)` 或批量 `writer.fill` |
| 列表 + 普通变量混填（如带汇总的报表） | 复杂填充 + `FillConfig.forceNewRow(true)`    |
| 大数据量报表                          | 列表放最后 + `WriteTable` 追加统计           |
| 动态列数（横向展开）                  | `FillConfig.direction(HORIZONTAL)`           |
| 多个不同列表同表展示                  | `FillWrapper` + 前缀                         |

### 4.3 通用注意事项

1. **监听器不能由 Spring 管理**：每次读取都需 `new` 一个新实例，监听器是有状态的。
2. **ExcelWriter 必须关闭**：使用 `try-with-resources` 确保资源释放。
3. **POI 依赖冲突**：若项目已引入 POI，需手动排除以避免版本冲突。
4. **Sheet 名称长度**：Excel 限制 31 个字符，按名称读取时使用真实名称。
5. **`forceNewRow` 的取舍**：开启后更安全（不覆盖后续内容）但性能略降；关闭时性能更好，适合列表在模板末尾的场景。
6. **批量入库阈值**：`PageReadListener` 默认 100 条，建议根据数据库写入性能调整（500 - 2000）。

---

## 五、参考链接

- 官方文档首页：<https://fesod.apache.org/docs/>
- 下载页面：<https://fesod.apache.org/docs/download>
- 快速开始：<https://fesod.apache.org/docs/quickstart/guide>
- 简单示例：<https://fesod.apache.org/docs/quickstart/simple-example>
- 简单读取：<https://fesod.apache.org/docs/sheet/read/simple>
- Sheet 读取：<https://fesod.apache.org/docs/sheet/read/sheet>
- 表头读取：<https://fesod.apache.org/docs/sheet/read/head>
- POJO 读取：<https://fesod.apache.org/docs/sheet/read/pojo>
- 转换器：<https://fesod.apache.org/docs/sheet/read/converter>
- 格式化：<https://fesod.apache.org/docs/sheet/read/format>
- 行数限制：<https://fesod.apache.org/docs/sheet/read/num-rows>
- CSV 读取：<https://fesod.apache.org/docs/sheet/read/csv>
- 额外信息读取：<https://fesod.apache.org/docs/sheet/read/extra>
- 异常处理：<https://fesod.apache.org/docs/sheet/read/exception>
- Spring 集成：<https://fesod.apache.org/docs/sheet/read/spring>
- 模板填充：<https://fesod.apache.org/docs/sheet/fill/>
