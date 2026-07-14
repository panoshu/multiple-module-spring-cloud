package core.test;

import com.alibaba.excel.EasyExcel;
import core.application.service.ExcelExchangeAppService;
import core.domain.model.*;
import core.domain.outbound.TemplateRepositoryPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.FieldType;
import core.domain.rule.HeaderMapping;
import infrastructure.excel.EasyExcelOutboundAdapter;
import infrastructure.schema.NetworkntSchemaValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComplexFormIntegrationTest {

  private final String INBOUND_ID = "CORP_PLAN_IN_V1";
  private final String OUTBOUND_ID = "CORP_PLAN_OUT_V1";
  private final String TEST_IN_FILE = "target/test_inbound_complex.xlsx";
  private final String TEST_OUT_FILE = "target/test_outbound_complex.xlsx";
  private ExcelExchangeAppService appService;

  @BeforeEach
  void setUp() {
    // === 1. 构造输入模板 ===
    HeaderZone inHeader = new HeaderZone(2, List.of(
      new HeaderMapping("B1", "$.header.planNo", FieldType.STRONG),
      new HeaderMapping("B2", "$.header.clientNo", FieldType.STRONG)
    ));
    // 输入端：不需要写回表头行，参数传 0 即可
    DetailZone inDetail = new DetailZone(8, 0, 0, "====明细结束====", null, List.of(
      new DetailMapping("A", "$.details[*].seq", FieldType.STRONG, "XH", "序号", null),
      new DetailMapping("B", "$.details[*].name", FieldType.STRONG, "XM", "姓名", null)
    ), null);
    String schema = "{\"type\": \"object\",\"properties\": {\"header\": {\"type\": \"object\"},\"details\": {\"type\": \"array\", \"items\": {\"type\": \"object\"}}}}";

    InboundTemplate inTemplate = new InboundTemplate(INBOUND_ID, OUTBOUND_ID, new InboundRule(inHeader, inDetail), schema);

    // === 2. 构造输出模板 ===
    HeaderZone outHeader = new HeaderZone(3, List.of(
      new HeaderMapping("B1", "$.header.planNo", FieldType.STRONG),
      new HeaderMapping("B2", "$.header.clientNo", FieldType.STRONG)
    ));
    List<StaticTextMapping> staticTexts = List.of(
      new StaticTextMapping("A1", "企业计划编号："),
      new StaticTextMapping("A2", "企业客户号："),
      new StaticTextMapping("A4", "==== 以下为系统增强审核导出结果 ====")
    );
    DetailZone outDetail = new DetailZone(8, 5, 7, null, null, List.of(
      new DetailMapping("A", "$.details[*].seq", FieldType.STRONG, "XH", "序号", "序号*"),
      new DetailMapping("B", "$.details[*].name", FieldType.STRONG, "XM", "姓名", "个人姓名*"),
      new DetailMapping("C", "$.details[*].idType", FieldType.STRONG, "ZJLX", "证件类型", "证件类型*"),
      new DetailMapping("D", "$.details[*].sysStatus", FieldType.STRONG, "status", "系统校验状态", "系统校验状态"),
      new DetailMapping("E", "$.details[*].sysTime", FieldType.STRONG, "time", "处理时间", "处理时间")
    ), null);

    OutboundTemplate outTemplate = new OutboundTemplate(OUTBOUND_ID, new OutboundRule(ExportEngineType.STREAM, "", staticTexts, outHeader, outDetail));

    // === 3. 注册仓储并实例化 ===
    TemplateRepositoryPort mockRepository = new TemplateRepositoryPort() {
      @Override
      public Optional<InboundTemplate> loadInbound(String templateId) {
        return templateId.equals(INBOUND_ID) ? Optional.of(inTemplate) : Optional.empty();
      }

      @Override
      public Optional<OutboundTemplate> loadOutbound(String templateId) {
        return templateId.equals(OUTBOUND_ID) ? Optional.of(outTemplate) : Optional.empty();
      }

      @Override
      public void clearCache(String templateId) {
      }
    };

    appService = new ExcelExchangeAppService(mockRepository, new NetworkntSchemaValidatorAdapter(), List.of(new EasyExcelOutboundAdapter()));
  }

  @Test
  void testComplexForm() throws Exception {
    generateMockComplexFile();

    // 引入新的参数: 文件名 TEST_IN_FILE
    ParseResult result;
    try (FileInputStream fis = new FileInputStream(TEST_IN_FILE)) {
      result = appService.parseExcel(INBOUND_ID, fis, TEST_IN_FILE);
    }

    String businessJson = result.jsonPayload().replace(
      "\"name\":\"张三\"",
      "\"name\":\"张三\",\"sysStatus\":\"已认证\",\"sysTime\":\"2026-05-24\""
    );

    try (FileOutputStream fos = new FileOutputStream(TEST_OUT_FILE)) {
      appService.exportExcel(INBOUND_ID, businessJson, fos);
    }
  }

  private void generateMockComplexFile() {
    // (省略，与你原有代码保持一致)
    List<List<Object>> sheet = new ArrayList<>();
    sheet.add(List.of("企业计划编号：", "0200010001", "企业计划名称：", "企业计划A"));
    sheet.add(List.of("企业客户号：", "000234", "企业客户名称：", "客户A"));
    sheet.add(List.of());
    sheet.add(List.of("请在填写表单之前仔细阅读填表说明，以免造成提交出错！"));
    sheet.add(List.of("XH", "XM", "ZJLX", "ZJHM"));
    sheet.add(List.of("基本信息"));
    sheet.add(List.of("序号*", "个人姓名*", "证件类型*", "证件编号*"));
    sheet.add(List.of("1", "张三", "身份证", "999000198608060000"));
    sheet.add(List.of("2", "李四", "护照", "G1234567"));
    sheet.add(List.of("====明细结束===="));
    EasyExcel.write(TEST_IN_FILE).sheet("Sheet1").doWrite(sheet);
  }
}
