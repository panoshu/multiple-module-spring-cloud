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
import com.example.file.domain.service.*;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import com.example.file.infrastructure.gateway.FesodExcelExporter;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 2: 全流程 happy path 测试 (解析→校验→拆分→导出→round-trip).
 *
 * <p>验证完整业务链路：
 * <ol>
 *   <li>用 RegionStateMachine 解析示例表单 → CanonicalData</li>
 *   <li>用 DataValidator 校验每行员工数据 (idNo/name 非空)</li>
 *   <li>用 TaskSplitter 按 idType 拆分为 2 个子任务 (身份证/护照)</li>
 *   <li>用 FesodExcelExporter 把每个 SplitUnit 导出为 Excel</li>
 *   <li>重新解析导出的 Excel，验证 properties 和 tables 与原 SplitUnit 一致</li>
 * </ol>
 *
 * <p>模板由 {@code @BeforeAll} 程序生成到 {@code docs/excel/示例表单_填充模板.xlsx}，
 * 占位符严格遵循 fesod 2.0.2 语法 (无前导点)。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportFlowIntegrationTest {

  private static final String SOURCE_EXCEL_PATH =
      Path.of("docs", "excel", "示例表单.xlsx").toString();
  private static final String FILL_TEMPLATE_PATH =
      Path.of("docs", "excel", "示例表单_填充模板.xlsx").toString();
  private static final String OUTPUT_DIR =
      Path.of("target", "test-classes").toString();

  /**
   * 程序生成填充模板，避免二进制文件入库。
   *
   * <p>模板结构 (5 行 4 列)：
   * <pre>
   * 行 0: 员工信息表                                                 (标题行，fesod 默认 headRowNumber=1 会跳过此行)
   * 行 1: 企业客户号： | {customerNo}    | 企业客户名称： | {customerName}    (KV，label 和 placeholder 分单元格)
   * 行 2: 序号       | 姓名            | 证件类型      | 证件号码           (静态表头)
   * 行 3: {employees.seq} | {employees.name} | {employees.idType} | {employees.idNo}  (列表占位符行，无前导点)
   * 行 4: 填表人：   | {filler}        | 复核人：      | {reviewer}        (KV，label 和 placeholder 分单元格)
   * </pre>
   *
   * <p>关键设计：
   * <ul>
   *   <li>label 和 placeholder 必须放在不同单元格，否则导出后 cell 内容会变成
   *       "label+value" 合并字符串，round-trip 解析时 fingerprint 精确匹配会失败。</li>
   *   <li>模板首行必须有内容 (标题行)，否则 {@code ExcelParserImpl.openStream} 因
   *       fesod 默认 {@code headRowNumber=1} 会跳过首行 KV，导致 round-trip 解析失败。</li>
   * </ul>
   */
  @BeforeAll
  void ensureFillTemplateExists() throws Exception {
    Path templatePath = Path.of(FILL_TEMPLATE_PATH);
    Files.createDirectories(templatePath.getParent());
    if (Files.exists(templatePath)) return;

    List<List<Object>> templateRows = new ArrayList<>();
    // 标题行：作为 fesod 默认 headRowNumber=1 的 "head" 被跳过，确保后续 KV 行不被丢弃
    templateRows.add(Arrays.asList("员工信息表", null, null, null));
    templateRows.add(Arrays.asList("企业客户号：", "{customerNo}", "企业客户名称：", "{customerName}"));
    templateRows.add(Arrays.asList("序号", "姓名", "证件类型", "证件号码"));
    templateRows.add(Arrays.asList("{employees.seq}", "{employees.name}", "{employees.idType}", "{employees.idNo}"));
    templateRows.add(Arrays.asList("填表人：", "{filler}", "复核人：", "{reviewer}"));
    FesodSheet.write(templatePath.toString()).sheet().doWrite(templateRows);
  }

  @Test
  void 全流程_解析_校验_拆分_导出_round_trip() throws Exception {
    // ===== 1. 解析示例表单 =====
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

    // 验证解析结果（基于 Step 1 探针实测值）
    assertThat(data.properties())
        .containsEntry("customerNo", "000234")
        .containsEntry("customerName", "客户A")
        .containsEntry("filler", "张三")
        .containsEntry("reviewer", "李四");
    assertThat(data.tables()).containsKey("employees");
    assertThat(data.tables().get("employees")).hasSize(3);
    // 第 1 行：seq=1, name=张内Aa01, idType=身份证
    assertThat(data.tables().get("employees").get(0))
        .containsEntry("seq", "1")
        .containsEntry("name", "张内Aa01")
        .containsEntry("idType", "身份证")
        .containsEntry("idNo", "999000198608060000");
    // 第 2 行：seq=2, idType=护照
    assertThat(data.tables().get("employees").get(1))
        .containsEntry("seq", "2")
        .containsEntry("idType", "护照");
    // 第 3 行：seq=3, idType=身份证
    assertThat(data.tables().get("employees").get(2))
        .containsEntry("seq", "3")
        .containsEntry("idType", "身份证");

    // ===== 2. 校验通过 (idNo/name 非空) =====
    List<ValidationRule> rules = List.of(
        new ValidationRule("idNo", ValidationScope.ROW, "idNo != null", "证件编号不能为空", FieldType.STRING),
        new ValidationRule("name", ValidationScope.ROW, "name != null", "姓名不能为空", FieldType.STRING));
    ExpressionEvaluator evaluator = buildExpressionEvaluator();
    DataValidator validator = new DataValidator();
    for (Map<String, Object> row : data.tables().get("employees")) {
      ValidationResult result = validator.validate(row, rules, ErrorPolicy.COLLECT_ALL, evaluator);
      assertThat(result.isValid()).isTrue();
    }

    // ===== 3. 按 idType 拆分 =====
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.putAll(data.properties());
    dataMap.putAll(data.tables());
    SplitConfig splitConfig = new SplitConfig(
        List.of("idType"),
        new SplitKeyDef("idType", "employees.idType", SplitKeyType.FIELD_VALUE),
        SplitMissPolicy.ERROR, null, null, false, 1000);
    TaskSplitter splitter = new TaskSplitter();
    List<SplitUnit> units = splitter.split(dataMap, splitConfig);

    // 实际数据：3 行员工 → 2 行身份证 (seq=1,3) + 1 行护照 (seq=2)
    assertThat(units).hasSize(2);
    assertThat(units).extracting(SplitUnit::splitKey)
        .containsExactlyInAnyOrder("身份证", "护照");

    // ===== 4. 每个 SplitUnit 导出 + 5. round-trip 验证 =====
    ExcelExporter exporter = new FesodExcelExporter();
    Path outputDir = Path.of(OUTPUT_DIR);
    Files.createDirectories(outputDir);

    for (SplitUnit unit : units) {
      Path outputPath = outputDir.resolve("export-" + unit.splitKey() + ".xlsx");
      Files.deleteIfExists(outputPath);

      try (InputStream tpl = new FileInputStream(FILL_TEMPLATE_PATH);
           OutputStream out = new FileOutputStream(outputPath.toFile())) {
        exporter.export(unit, tpl, out);
      }
      assertThat(Files.exists(outputPath)).isTrue();
      assertThat(Files.size(outputPath)).isGreaterThan(0);

      // round-trip: 重新解析导出的 Excel
      List<RegionDef> roundTripRegions = buildRoundTripRegionDefs();
      CanonicalData reParsed;
      try (InputStream is = new FileInputStream(outputPath.toFile())) {
        RawRowStream stream = excelParser.openStream(is);
        List<RegionParseResult> results = stateMachine.drive(stream, roundTripRegions, new ParseContext(roundTripRegions));
        reParsed = new CanonicalModelBuilder().build(results, roundTripRegions);
      }

      // 验证 properties 一致 (customerNo/customerName/filler/reviewer)
      assertThat(reParsed.properties())
          .containsEntry("customerNo", unit.data().get("customerNo"))
          .containsEntry("customerName", unit.data().get("customerName"))
          .containsEntry("filler", unit.data().get("filler"))
          .containsEntry("reviewer", unit.data().get("reviewer"));

      // 验证 tables 行数一致
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> expectedRows = (List<Map<String, Object>>) unit.data().get("employees");
      assertThat(reParsed.tables().get("employees")).hasSize(expectedRows.size());

      // 验证每行字段值一致 (seq/name/idType/idNo)
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

  /**
   * 源 Excel 解析配置（基于 Step 1 探针实测结构）。
   *
   * <p>表头是 3 行结构：Row 3=代码 (XH/XM/ZJLX/ZJHM) + Row 4=分组 + Row 5=中文带星号。
   * 所以 headerRows=3, headerNameRow=1 (1-indexed, 1st row = 代码行)。
   *
   * <p>filler label 实测为英文冒号 {@code 填表人:}，reviewer 为中文冒号 {@code 复核人：}。
   */
  private List<RegionDef> buildSourceRegionDefs() {
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

  /**
   * Round-trip 解析配置（用于解析导出后的 Excel）。
   *
   * <p>导出模板只有 1 行表头 (序号/姓名/证件类型/证件号码)，所以 headerRows=1, headerNameRow=0。
   * 模板中 filler/reviewer 均用中文冒号，所以 round-trip aliases 也用中文冒号。
   * 模板无数据结束标记，dataEnd=null。
   */
  private List<RegionDef> buildRoundTripRegionDefs() {
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
