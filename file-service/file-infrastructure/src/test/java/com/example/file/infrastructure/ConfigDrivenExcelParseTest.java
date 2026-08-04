package com.example.file.infrastructure;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.service.*;
import com.example.file.infrastructure.gateway.AviatorExpressionEvaluator;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import com.example.file.infrastructure.gateway.FesodExcelExporter;
import com.example.file.infrastructure.gateway.YamlConfigLoader;
import com.example.file.types.BizType;
import com.example.shared.identifier.id.UserNo;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置驱动的 Excel 解析全流程测试.
 *
 * <p>与 {@link ExportFlowIntegrationTest} 和 {@link ParseFlowIntegrationTest} 的区别：
 * <ul>
 *   <li>那两个测试用 Java 代码硬编码 {@link RegionDef}，本测试从 YAML 配置文件加载</li>
 *   <li>验证生产场景：YAML 文件作为初始源 → {@link YamlConfigLoader} → {@link TemplateConfig}</li>
 *   <li>覆盖完整业务链路：加载配置 → 解析 Excel → Aviator 校验 → 按 idType 拆分 → FesodExcelExporter 导出 → round-trip</li>
 * </ul>
 *
 * <p>配置文件（位于 {@code docs/模板配置/配置示例/}）：
 * <ul>
 *   <li>业务基线：{@code 具体业务基线配置-enterprise_plan_base.yaml}</li>
 *   <li>源模板：{@code 具体的源模板配置-ENTERPRISE_PLAN_STANDARD.yaml}</li>
 *   <li>被解析 Excel：{@code docs/excel/示例表单.xlsx}</li>
 * </ul>
 *
 * <p>导出模板由 {@code @BeforeAll} 程序生成到 JUnit {@code @TempDir}，避免二进制文件入库。
 * Round-trip 解析所需的 {@link RegionDef} 不来自 YAML（因导出模板是程序生成，结构与源 Excel 不同），
 * 在 {@link #buildRoundTripRegionDefs()} 中单独构建。
 */
class ConfigDrivenExcelParseTest {

  private static final String BASELINE_YAML_PATH =
    Path.of("docs", "模板配置", "配置示例", "具体业务基线配置-enterprise_plan_base.yaml").toString();
  private static final String SOURCE_TEMPLATE_YAML_PATH =
    Path.of("docs", "模板配置", "配置示例", "具体的源模板配置-ENTERPRISE_PLAN_STANDARD.yaml").toString();
  private static final String SOURCE_EXCEL_PATH =
    Path.of("docs", "excel", "示例表单.xlsx").toString();
  private static final String OUTPUT_DIR =
    Path.of("target", "test-output", "config-driven").toString();

  private static Path fillTemplatePath;

  /**
   * 程序生成填充模板到 JUnit {@code @TempDir}，避免二进制文件入库和源代码树污染.
   *
   * <p>模板结构（4 行 4 列）：
   * <pre>
   * 行 0: 企业客户号： | {customerNo} | 企业客户名称： | {customerName}    (KV，label 和 placeholder 分单元格)
   * 行 1: 序号       | 姓名         | 证件类型       | 证件号码           (静态表头)
   * 行 2: {employees.seq} | {employees.name} | {employees.idType} | {employees.idNo}  (列表占位符行)
   * 行 3: 填表人：   | {filler}     | 复核人：       | {reviewer}         (KV，label 和 placeholder 分单元格)
   * </pre>
   *
   * <p>关键设计（与 {@link ExportFlowIntegrationTest} 一致）：
   * <ul>
   *   <li>label 和 placeholder 必须放在不同单元格，否则导出后 cell 内容会变成 "label+value" 合并字符串</li>
   *   <li>filler/reviewer 均用中文冒号，round-trip 解析时 aliases 也用中文冒号</li>
   * </ul>
   */
  @BeforeAll
  static void generateFillTemplate(@TempDir Path tempDir) throws Exception {
    fillTemplatePath = tempDir.resolve("示例表单_填充模板.xlsx");
    List<List<Object>> templateRows = new ArrayList<>();
    templateRows.add(Arrays.asList("企业客户号：", "{customerNo}", "企业客户名称：", "{customerName}"));
    templateRows.add(Arrays.asList("序号", "姓名", "证件类型", "证件号码"));
    templateRows.add(Arrays.asList("{employees.seq}", "{employees.name}", "{employees.idType}", "{employees.idNo}"));
    templateRows.add(Arrays.asList("填表人：", "{filler}", "复核人：", "{reviewer}"));
    FesodSheet.write(fillTemplatePath.toString()).sheet().doWrite(templateRows);
  }

  @Test
  @DisplayName("加载 YAML 配置文件构建 TemplateConfig")
  void loadYamlConfig_读取配置文件_构建TemplateConfig() throws Exception {
    TemplateConfig config = loadConfigFromYaml();

    // 验证基础字段
    assertThat(config.bizType().value()).isEqualTo("ENTERPRISE_PLAN");
    assertThat(config.templateVersion()).isEqualTo("1.0");
    assertThat(config.errorPolicy()).isEqualTo(ErrorPolicy.COLLECT_ALL);

    // 验证 canonicalModel（6 properties + 1 table）
    assertThat(config.canonicalModel().properties()).hasSize(6);
    assertThat(config.canonicalModel().properties())
      .extracting(p -> p.code())
      .contains("planNo", "planName", "customerNo", "customerName", "filler", "reviewer");
    assertThat(config.canonicalModel().tables()).hasSize(1);
    assertThat(config.canonicalModel().tables().get(0).code()).isEqualTo("employees");
    assertThat(config.canonicalModel().tables().get(0).fields()).hasSize(4);

    // 验证 validationRules（idNo + name 非空校验）
    assertThat(config.validationRules()).hasSize(2);
    assertThat(config.validationRules())
      .extracting(ValidationRule::field)
      .contains("idNo", "name");

    // 验证 splitConfig（按 idType 拆分）
    assertThat(config.splitConfig().keys()).contains("idType");
    assertThat(config.splitConfig().splitKey().sourcePath()).isEqualTo("employees.idType");

    // 验证 sourceTemplates（1 个 ENTERPRISE_PLAN_STANDARD + 3 个 regions）
    assertThat(config.sourceTemplates()).hasSize(1);
    assertThat(config.sourceTemplates().get(0).id().value()).isEqualTo("ENTERPRISE_PLAN_STANDARD");
    assertThat(config.sourceTemplates().get(0).regions()).hasSize(3);
    assertThat(config.sourceTemplates().get(0).regions())
      .extracting(RegionDef::name)
      .contains("basic_info", "employee_list", "filler_info");
  }

  @Test
  @DisplayName("用 YAML 配置驱动解析示例表单 → 验证 properties + tables")
  void parseExcel_withYamlConfig_完整解析流程() throws Exception {
    TemplateConfig config = loadConfigFromYaml();
    List<RegionDef> regions = config.sourceTemplates().get(0).regions();
    CanonicalData data = parseExcel(SOURCE_EXCEL_PATH, regions);

    // 验证 properties（来自 basic_info + filler_info 两个 KV 区域）
    assertThat(data.properties())
      .containsEntry("customerNo", "000234")
      .containsEntry("customerName", "客户A")
      .containsEntry("filler", "张三")
      .containsEntry("reviewer", "李四");

    // 验证 tables.employees（来自 employee_list TABLE 区域，3 行数据）
    assertThat(data.tables()).containsKey("employees");
    assertThat(data.tables().get("employees")).hasSize(3);

    // 验证字段名是标准名（seq/name/idType/idNo），非 Excel 列代码（XH/XM/ZJLX/ZJHM）
    assertThat(data.tables().get("employees").get(0))
      .containsEntry("seq", "1")
      .containsEntry("name", "张内Aa01")
      .containsEntry("idType", "身份证")
      .containsEntry("idNo", "999000198608060000");
    assertThat(data.tables().get("employees").get(1))
      .containsEntry("seq", "2")
      .containsEntry("idType", "护照");
    assertThat(data.tables().get("employees").get(2))
      .containsEntry("seq", "3")
      .containsEntry("idType", "身份证");
  }

  @Test
  @DisplayName("用 YAML 配置的 validationRules + Aviator 校验解析后的数据")
  void parseExcel_withYamlConfig_校验流程() throws Exception {
    TemplateConfig config = loadConfigFromYaml();
    List<RegionDef> regions = config.sourceTemplates().get(0).regions();
    CanonicalData data = parseExcel(SOURCE_EXCEL_PATH, regions);

    // 用 config 中的 validationRules + 真实 Aviator 引擎校验
    List<ValidationRule> rules = config.validationRules();
    ExpressionEvaluator evaluator = new AviatorExpressionEvaluator();
    DataValidator validator = new DataValidator();

    // 3 行员工数据均应校验通过（idNo 和 name 都非空）
    for (Map<String, Object> row : data.tables().get("employees")) {
      ValidationResult result = validator.validate(row, rules, config.errorPolicy(), evaluator);
      assertThat(result.isValid()).isTrue();
    }
  }

  @Test
  @DisplayName("用 YAML 配置的 splitConfig 按 idType 拆分为 2 个子任务")
  void parseExcel_withYamlConfig_拆分流程() throws Exception {
    TemplateConfig config = loadConfigFromYaml();
    List<RegionDef> regions = config.sourceTemplates().get(0).regions();
    CanonicalData data = parseExcel(SOURCE_EXCEL_PATH, regions);

    // 展平 CanonicalData 为 Map 供 TaskSplitter 使用
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.putAll(data.properties());
    dataMap.putAll(data.tables());

    // 用 config 中的 splitConfig 拆分
    TaskSplitter splitter = new TaskSplitter();
    List<SplitUnit> units = splitter.split(dataMap, config.splitConfig());

    // 实际数据：3 行员工，2 行 idType=身份证，1 行 idType=护照 → 拆分为 2 个子任务
    assertThat(units).hasSize(2);
    assertThat(units).extracting(SplitUnit::splitKey)
      .containsExactlyInAnyOrder("身份证", "护照");
  }

  @Test
  @DisplayName("用 YAML 配置完整流程：解析 → 校验 → 拆分 → FesodExcelExporter 导出 → round-trip")
  void parseExcel_withYamlConfig_导出流程() throws Exception {
    TemplateConfig config = loadConfigFromYaml();
    List<RegionDef> regions = config.sourceTemplates().get(0).regions();
    CanonicalData data = parseExcel(SOURCE_EXCEL_PATH, regions);

    // 1. 校验（用 config.validationRules + Aviator）
    List<ValidationRule> rules = config.validationRules();
    ExpressionEvaluator evaluator = new AviatorExpressionEvaluator();
    DataValidator validator = new DataValidator();
    for (Map<String, Object> row : data.tables().get("employees")) {
      ValidationResult result = validator.validate(row, rules, config.errorPolicy(), evaluator);
      assertThat(result.isValid()).isTrue();
    }

    // 2. 拆分（用 config.splitConfig）
    Map<String, Object> dataMap = new LinkedHashMap<>();
    dataMap.putAll(data.properties());
    dataMap.putAll(data.tables());
    TaskSplitter splitter = new TaskSplitter();
    List<SplitUnit> units = splitter.split(dataMap, config.splitConfig());
    assertThat(units).hasSize(2);

    // 3. 每个 SplitUnit 导出 + 4. round-trip 验证
    ExcelExporter exporter = new FesodExcelExporter();
    Path outputDir = Path.of(OUTPUT_DIR);
    Files.createDirectories(outputDir);

    for (SplitUnit unit : units) {
      Path outputPath = outputDir.resolve("export-" + unit.splitKey() + ".xlsx");
      Files.deleteIfExists(outputPath);

      try (InputStream tpl = new FileInputStream(fillTemplatePath.toFile());
           OutputStream out = new FileOutputStream(outputPath.toFile())) {
        exporter.export(unit, tpl, out);
      }
      assertThat(Files.exists(outputPath)).isTrue();
      assertThat(Files.size(outputPath)).isGreaterThan(0);

      // round-trip: 重新解析导出的 Excel（用 round-trip 专用 RegionDef）
      List<RegionDef> roundTripRegions = buildRoundTripRegionDefs();
      CanonicalData reParsed = parseExcel(outputPath.toString(), roundTripRegions);

      // 验证 properties 一致（customerNo/customerName/filler/reviewer）
      assertThat(reParsed.properties())
        .containsEntry("customerNo", unit.data().get("customerNo"))
        .containsEntry("customerName", unit.data().get("customerName"))
        .containsEntry("filler", unit.data().get("filler"))
        .containsEntry("reviewer", unit.data().get("reviewer"));

      // 验证 tables 行数和字段值一致（seq/name/idType/idNo）
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> expectedRows = (List<Map<String, Object>>) unit.data().get("employees");
      assertThat(reParsed.tables().get("employees")).hasSize(expectedRows.size());
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

  // ============ Helper Methods ============

  /**
   * 从 YAML 配置文件加载 {@link TemplateConfig}.
   *
   * <p>路径常量见 {@link #BASELINE_YAML_PATH} / {@link #SOURCE_TEMPLATE_YAML_PATH}。
   * 使用 {@link Files#readString} 读取 UTF-8 编码的 YAML 文件。
   */
  private TemplateConfig loadConfigFromYaml() throws Exception {
    String baselineYaml = Files.readString(Path.of(BASELINE_YAML_PATH));
    String sourceTemplateYaml = Files.readString(Path.of(SOURCE_TEMPLATE_YAML_PATH));
    ConfigLoader loader = new YamlConfigLoader();
    return loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"),
      baselineYaml,
      List.of(sourceTemplateYaml),
      "1.0",
      UserNo.of("test-user"));
  }

  /**
   * 用指定 {@link RegionDef} 列表解析 Excel 文件，返回 {@link CanonicalData}.
   *
   * <p>封装 {@link ExcelParserImpl} + {@link RegionStateMachine} + {@link CanonicalModelBuilder}
   * 三步流程，供各测试用例复用。
   */
  private CanonicalData parseExcel(String excelPath, List<RegionDef> regions) throws Exception {
    ExcelParser excelParser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
      RegionType.KEY_VALUE, new KeyValueRegionParser(),
      RegionType.TABLE, new TableRegionParser()));
    try (InputStream is = new FileInputStream(excelPath)) {
      RawRowStream stream = excelParser.openStream(is);
      List<RegionParseResult> results = stateMachine.drive(stream, regions, new ParseContext(regions));
      return new CanonicalModelBuilder().build(results, regions);
    }
  }

  /**
   * Round-trip 解析配置（用于解析导出后的 Excel）.
   *
   * <p>导出模板结构与源 Excel 不同：
   * <ul>
   *   <li>只有 1 行表头（序号/姓名/证件类型/证件号码），所以 headerRows=1, headerNameRow=0</li>
   *   <li>filler/reviewer 均用中文冒号（与填充模板一致）</li>
   *   <li>无数据结束标记，dataEnd=null</li>
   * </ul>
   *
   * <p>注意：此配置不来自 YAML，因为导出模板是程序生成的，与源 Excel 结构不同。
   * 如需配置化，可新增一份 "round-trip 源模板.yaml"，但当前场景下测试代码构建更直接。
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
}
