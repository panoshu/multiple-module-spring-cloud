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
import com.example.file.domain.service.*;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

      // 验证 properties（实际文件只有 customerNo/customerName 两组 KV）
      assertThat(data.properties())
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
      ExpressionEvaluator evaluator = (expr, ctxMap) -> {
        if (expr == null) return true;
        if (expr.endsWith("!= null")) {
          String field = expr.substring(0, expr.indexOf("!=")).trim();
          return ctxMap.containsKey(field) && ctxMap.get(field) != null;
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
  void 拆分流程_按idType拆分() throws Exception {
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

      // 拆分配置：sourcePath = "employees.idType"
      // 实际数据：3 行员工，2 行 idType=身份证，1 行 idType=护照
      SplitConfig splitConfig = new SplitConfig(
        List.of("idType"),
        new SplitKeyDef("idType", "employees.idType", SplitKeyType.FIELD_VALUE),
        SplitMissPolicy.ERROR, null, null, false, 1000);

      TaskSplitter splitter = new TaskSplitter();
      List<SplitUnit> subTasks = splitter.split(dataMap, splitConfig);

      // 按证件类型拆分为 2 个子任务：身份证(2 行) + 护照(1 行)
      assertThat(subTasks).hasSize(2);
      assertThat(subTasks).extracting(SplitUnit::splitKey)
        .containsExactlyInAnyOrder("身份证", "护照");
    }
  }

  private List<RegionDef> buildRegionDefs() {
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
}
