package core.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.application.service.ExcelExchangeAppService;
import core.domain.model.*;
import core.domain.outbound.TemplateRepositoryPort;
import infrastructure.excel.EasyExcelOutboundAdapter;
import infrastructure.schema.NetworkntSchemaValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class RealFilesIntegrationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final String REAL_EXCEL_IN_PATH = "D:/temp/示例表单.xlsx";
  private final String REAL_EXCEL_OUT_PATH = "D:/temp/示例表单_导出结果.xlsx";
  private final String INBOUND_ID = "CORP_PLAN_IN_V1";
  private final String OUTBOUND_ID = "CORP_PLAN_OUT_V1";
  private ExcelExchangeAppService appService;

  @BeforeEach
  void setUp() throws Exception {
    InputStream configStream = this.getClass().getClassLoader().getResourceAsStream("template_corp_plan.json");
    if (configStream == null) {
      throw new RuntimeException("找不到配置文件");
    }

    // 动态解析
    JsonNode rootNode = mapper.readTree(configStream);
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
  void testRealFileExchange() throws Exception {
    File inFile = new File(REAL_EXCEL_IN_PATH);
    if (!inFile.exists()) {
      return;
    }

    ParseResult result;
    try (FileInputStream fis = new FileInputStream(inFile)) {
      // 补充第三个参数: fileName
      result = appService.parseExcel(INBOUND_ID, fis, inFile.getName());
    }

    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(result.jsonPayload())));

    String businessProcessedJson = result.jsonPayload()
      .replace("}", ",\"sysStatus\":\"审核通过\",\"sysTime\":\"2026-05-24 16:30:00\"}");

    try (FileOutputStream fos = new FileOutputStream(REAL_EXCEL_OUT_PATH)) {
      appService.exportExcel(INBOUND_ID, businessProcessedJson, fos);
    }
  }
}
