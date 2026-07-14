package core.test;

import com.alibaba.excel.EasyExcel;
import core.application.service.ExcelExchangeAppService;
import core.domain.model.*;
import core.domain.outbound.TemplateRepositoryPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.FieldType;
import infrastructure.excel.EasyExcelOutboundAdapter;
import infrastructure.excel.PoiTemplateOutboundAdapter;
import infrastructure.schema.NetworkntSchemaValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DualEngineRoutingIntegrationTest {

  private final String IN_ID_FOR_STREAM = "IN_STREAM_V1";
  private final String IN_ID_FOR_TEMPLATE = "IN_TEMPLATE_V1";
  private final String OUT_STREAM_FILE = "target/out_by_stream.xlsx";
  private final String OUT_TEMPLATE_FILE = "target/out_by_template.xlsx";
  private final String BASE_DUMMY_TEMPLATE = "target/dummy_base_template.xlsx";
  private final String MOCK_JSON = """
    {
      "header": { "planNo": "PLN-999" },
      "details": [
        { "name": "张三", "idNo": "111" },
        { "name": "李四", "idNo": "222" }
      ]
    }
    """;
  private ExcelExchangeAppService appService;

  @BeforeEach
  void setUp() {
    // 0. 在磁盘造一个空的“精美底座模板”供 POI 使用
    createDummyPhysicalTemplate();

    // 1. 造流式输出配置 (STREAM)
    OutboundRule streamRule = new OutboundRule(ExportEngineType.STREAM, null, null,
      null, new DetailZone(2, 0, 1, null, null, List.of(
      new DetailMapping("A", "$.details[*].name", FieldType.STRONG, null, "姓名", "姓名"),
      new DetailMapping("B", "$.details[*].idNo", FieldType.STRONG, null, "证件", "证件")
    ), null));
    OutboundTemplate streamOutTemplate = new OutboundTemplate("OUT_STREAM_V1", streamRule);
    InboundTemplate streamInTemplate = new InboundTemplate(IN_ID_FOR_STREAM, "OUT_STREAM_V1", null, "{}");

    // 2. 造模板输出配置 (TEMPLATE)
    OutboundRule poiRule = new OutboundRule(ExportEngineType.TEMPLATE, BASE_DUMMY_TEMPLATE, null,
      null, new DetailZone(3, 0, 0, null, null, List.of( // 假设从第 3 行开始填空
      new DetailMapping("A", "$.details[*].name", FieldType.STRONG, null, null, null),
      new DetailMapping("B", "$.details[*].idNo", FieldType.STRONG, null, null, null)
    ), null));
    OutboundTemplate poiOutTemplate = new OutboundTemplate("OUT_TEMPLATE_V1", poiRule);
    InboundTemplate poiInTemplate = new InboundTemplate(IN_ID_FOR_TEMPLATE, "OUT_TEMPLATE_V1", null, "{}");

    // 3. 组装仓储
    TemplateRepositoryPort repo = new TemplateRepositoryPort() {
      @Override
      public Optional<InboundTemplate> loadInbound(String id) {
        if (id.equals(IN_ID_FOR_STREAM)) {
          return Optional.of(streamInTemplate);
        }
        if (id.equals(IN_ID_FOR_TEMPLATE)) {
          return Optional.of(poiInTemplate);
        }
        return Optional.empty();
      }

      @Override
      public Optional<OutboundTemplate> loadOutbound(String id) {
        if (id.equals("OUT_STREAM_V1")) {
          return Optional.of(streamOutTemplate);
        }
        if (id.equals("OUT_TEMPLATE_V1")) {
          return Optional.of(poiOutTemplate);
        }
        return Optional.empty();
      }

      @Override
      public void clearCache(String id) {
      }
    };

    // 4. 将两个引擎一起注册进容器！
    appService = new ExcelExchangeAppService(
      repo,
      new NetworkntSchemaValidatorAdapter(),
      List.of(new EasyExcelOutboundAdapter(), new PoiTemplateOutboundAdapter()) // ★ 依赖注入双引擎
    );
  }

  @Test
  void testAutoRouting() throws Exception {
    System.out.println(">>> 测试 1: 请求流式(EasyExcel)引擎导出...");
    try (FileOutputStream fos = new FileOutputStream(OUT_STREAM_FILE)) {
      // 引擎会读取 IN_ID_FOR_STREAM -> 找到 OUT_STREAM_V1 -> 发现 engineType=STREAM -> 走 EasyExcel
      appService.exportExcel(IN_ID_FOR_STREAM, MOCK_JSON, fos);
    }
    assertTrue(new File(OUT_STREAM_FILE).exists(), "流式导出文件应存在");
    System.out.println("   └─ 成功路由至 EasyExcel，生成文件: " + OUT_STREAM_FILE);

    System.out.println("\n>>> 测试 2: 请求底座模板(POI)引擎精准填空导出...");
    try (FileOutputStream fos = new FileOutputStream(OUT_TEMPLATE_FILE)) {
      // 引擎会读取 IN_ID_FOR_TEMPLATE -> 找到 OUT_TEMPLATE_V1 -> 发现 engineType=TEMPLATE -> 走 POI
      appService.exportExcel(IN_ID_FOR_TEMPLATE, MOCK_JSON, fos);
    }
    assertTrue(new File(OUT_TEMPLATE_FILE).exists(), "模板导出文件应存在");
    System.out.println("   └─ 成功路由至 POI，完成精准注入，生成文件: " + OUT_TEMPLATE_FILE);
  }

  private void createDummyPhysicalTemplate() {
    // 为 POI 引擎造一个带有颜色的假底座模板，假装它是实施人员画好的
    List<List<Object>> sheet = new ArrayList<>();
    sheet.add(List.of("========== 这是漂亮的报表抬头 =========="));
    sheet.add(List.of("系统姓名", "系统证件号", "预留背景列"));
    EasyExcel.write(BASE_DUMMY_TEMPLATE).sheet("Sheet1").doWrite(sheet);
  }
}
