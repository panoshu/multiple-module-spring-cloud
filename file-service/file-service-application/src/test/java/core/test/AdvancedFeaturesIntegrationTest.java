package core.test;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.application.service.ExcelExchangeAppService;
import core.application.service.JsonDataSplitter;
import core.domain.model.*;
import core.domain.outbound.TemplateRepositoryPort;
import infrastructure.excel.EasyExcelErrorExcelExporter;
import infrastructure.excel.EasyExcelOutboundAdapter;
import infrastructure.schema.NetworkntSchemaValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdvancedFeaturesIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final String INBOUND_ID = "CORP_PLAN_IN_V1";
  private final String OUTBOUND_ID = "CORP_PLAN_OUT_V1";
  private final String IN_FILE = "target/test_advanced_in.xlsx";
  private final String ERROR_OUT_FILE = "target/test_advanced_error_book.xlsx";
  private ExcelExchangeAppService appService;

  @BeforeEach
  void setUp() throws Exception {
    // 利用 Jackson 动态解析老的 JSON，自动拆分为新的分离模板
    JsonNode rootNode = mapper.readTree(this.getClass().getClassLoader().getResourceAsStream("template_corp_plan.json"));

    InboundRule inboundRule = mapper.treeToValue(rootNode.get("inboundRule"), InboundRule.class);
    String schema = rootNode.get("jsonSchema").asText();
    InboundTemplate inTemplate = new InboundTemplate(INBOUND_ID, OUTBOUND_ID, inboundRule, schema);

    OutboundRule outboundRule = mapper.treeToValue(rootNode.get("outboundRule"), OutboundRule.class);
    OutboundTemplate outTemplate = new OutboundTemplate(OUTBOUND_ID, outboundRule);

    TemplateRepositoryPort repo = new TemplateRepositoryPort() {
      @Override
      public Optional<InboundTemplate> loadInbound(String id) {
        return id.equals(INBOUND_ID) ? Optional.of(inTemplate) : Optional.empty();
      }

      @Override
      public Optional<OutboundTemplate> loadOutbound(String id) {
        return id.equals(OUTBOUND_ID) ? Optional.of(outTemplate) : Optional.empty();
      }

      @Override
      public void clearCache(String id) {
      }
    };

    appService = new ExcelExchangeAppService(repo, new NetworkntSchemaValidatorAdapter(), List.of(new EasyExcelOutboundAdapter()));
  }

  @Test
  void testAdvancedPipelineWithErrorExport() throws Exception {
    generateMockData();

    ParseResult result;
    try (FileInputStream fis = new FileInputStream(IN_FILE)) {
      // 新增 IN_FILE 参数
      result = appService.parseExcel(INBOUND_ID, fis, IN_FILE);
    }

    if (!result.isSuccess()) {
      System.out.println("生成错题本...");
      EasyExcelErrorExcelExporter errorExporter = new EasyExcelErrorExcelExporter();
      try (FileInputStream originFis = new FileInputStream(IN_FILE);
           FileOutputStream errorFos = new FileOutputStream(ERROR_OUT_FILE)) {
        errorExporter.exportErrorExcel(originFis, result.errors(), errorFos);
      }
      assertTrue(new File(ERROR_OUT_FILE).exists(), "错题本应该被创建");
    }

    Map<String, String> splitJsons = JsonDataSplitter.split(result.jsonPayload(), List.of("branchNo"));
    for (Map.Entry<String, String> entry : splitJsons.entrySet()) {
      String enhancedJson = entry.getValue().replace("}", ",\"sysStatus\":\"已分配给 " + entry.getKey() + "\"}");
      String outPath = "target/test_advanced_out_" + entry.getKey() + ".xlsx";
      try (FileOutputStream fos = new FileOutputStream(outPath)) {
        appService.exportExcel(INBOUND_ID, enhancedJson, fos);
      }
    }
  }

  private void generateMockData() {
    // (省略，与你原有代码保持一致)
    List<List<Object>> sheet = new ArrayList<>();
    sheet.add(List.of("企业计划编号：", "0200010001", "企业计划名称：", "集团全员保障计划"));
    sheet.add(List.of("企业客户号：", "000234", "企业客户名称：", "某某科技集团"));
    for (int i = 0; i < 5; i++) sheet.add(List.of());
    sheet.add(List.of("1", "张三", "身份证", "110105", "2099", "GH1", "男", "1990", "BJ01", "10000", "内勤", "2020", "2020"));
    sheet.add(List.of("2", "张三", "身份证", "110105", "2099", "GH2", "男", "1990", "BJ01", "20000", "内勤", "2021", "2021"));
    sheet.add(List.of("3", "李四", "护照", "G1234", "2099", "GH3", "女", "1992", "SH02", "15000", "外勤", "2019", "2019"));
    sheet.add(List.of("4", "王五", "身份证", "310115", "2099", "GH4", "男", "1995", "SH02", "不可转为数字", "外勤", "2022", "2022"));
    sheet.add(List.of("====明细结束===="));
    EasyExcel.write(IN_FILE).sheet("Sheet1").doWrite(sheet);
  }
}
