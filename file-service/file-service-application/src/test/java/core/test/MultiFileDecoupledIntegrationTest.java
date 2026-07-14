package core.test;

import com.alibaba.excel.EasyExcel;
import core.application.service.ExcelExchangeAppService;
import core.application.service.ExcelExchangeAppService.BatchFileRequest;
import core.domain.model.*;
import core.domain.outbound.TemplateRepositoryPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.FieldType;
import infrastructure.excel.EasyExcelOutboundAdapter;
import infrastructure.schema.NetworkntSchemaValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MultiFileDecoupledIntegrationTest {

  // A客户模板和B客户模板 共同指向 标准输出模板
  private final String INBOUND_ID_A = "CLIENT_A_V1";
  private final String INBOUND_ID_B = "CLIENT_B_V1";
  private final String OUTBOUND_ID_STD = "STD_EXPORT_V1";
  private final String FILE_A = "target/client_a_import.xlsx";
  private final String FILE_B = "target/client_b_import.xlsx";
  private final String MERGED_OUT_FILE = "target/merged_standard_export.xlsx";
  private ExcelExchangeAppService appService;

  @BeforeEach
  void setUp() {
    // ==========================================
    // 1. 造A客户的输入规则：A在第1列，B在第2列
    // ==========================================
    InboundRule ruleA = new InboundRule(
      new HeaderZone(1, List.of()),
      // DetailZone(startRow, fieldIdRow, titleRow, endRowMarker, uniqueKeys, fields, dynamicFields)
      // 输入时不需要写回表头，fieldIdRow 和 titleRow 传 0 即可
      new DetailZone(2, 0, 0, null, List.of("name", "idNo"), List.of(
        // DetailMapping(col, jsonPath, type, fieldId, label, exportTitle)
        // 补充 fieldId 和 exportTitle 为 null
        new DetailMapping("A", "$.details[*].name", FieldType.STRONG, null, "姓名", null),
        new DetailMapping("B", "$.details[*].idNo", FieldType.STRONG, null, "身份证", null)
      ), null)
    );
    InboundTemplate templateA = new InboundTemplate(INBOUND_ID_A, OUTBOUND_ID_STD, ruleA, "{}");

    // ==========================================
    // 2. 造B客户的输入规则：列位全变了，他把身份证放A列，姓名放C列！
    // ==========================================
    InboundRule ruleB = new InboundRule(
      new HeaderZone(1, List.of()),
      new DetailZone(2, 0, 0, null, List.of("name", "idNo"), List.of(
        new DetailMapping("C", "$.details[*].name", FieldType.STRONG, null, "申请人姓名", null),
        new DetailMapping("A", "$.details[*].idNo", FieldType.STRONG, null, "证件号", null)
      ), null)
    );
    InboundTemplate templateB = new InboundTemplate(INBOUND_ID_B, OUTBOUND_ID_STD, ruleB, "{}");

    // ==========================================
    // 3. 造统一的输出规则 (包含所有系统字段)
    // ==========================================
    OutboundRule stdOutRule = new OutboundRule(ExportEngineType.STREAM, "",
      null, // 无额外静态文本
      new HeaderZone(1, List.of()),
      // 输出时，假设我们把第 1 行作为导出中文名(titleRow=1)，第 2 行开始是数据(startRow=2)
      new DetailZone(2, 0, 1, null, null, List.of(
        // 补充 fieldId 为 null（这里不需要输出英文ID），然后是 label, 接着是 exportTitle
        new DetailMapping("A", "$.details[*].name", FieldType.STRONG, null, "标准名称", "全网统一姓名"),
        new DetailMapping("B", "$.details[*].idNo", FieldType.STRONG, null, "标准身份证", "全网统一证件号"),
        new DetailMapping("C", "$.details[*].sysStatus", FieldType.STRONG, null, "状态", "后台审核状态")
      ), null)
    );
    OutboundTemplate stdOutTemplate = new OutboundTemplate(OUTBOUND_ID_STD, stdOutRule);

    // 注册到内存仓储
    TemplateRepositoryPort repo = new TemplateRepositoryPort() {
      @Override
      public Optional<InboundTemplate> loadInbound(String id) {
        if (id.equals(INBOUND_ID_A)) {
          return Optional.of(templateA);
        }
        if (id.equals(INBOUND_ID_B)) {
          return Optional.of(templateB);
        }
        return Optional.empty();
      }

      @Override
      public Optional<OutboundTemplate> loadOutbound(String id) {
        return id.equals(OUTBOUND_ID_STD) ? Optional.of(stdOutTemplate) : Optional.empty();
      }

      @Override
      public void clearCache(String templateId) {
      }
    };

    appService = new ExcelExchangeAppService(repo, new NetworkntSchemaValidatorAdapter(), List.of(new EasyExcelOutboundAdapter()));
  }

  @Test
  void testDecoupledAndCrossValidation() throws Exception {
    // 1. 造物理文件
    generateMockFiles();

    // 2. 将多个文件打包请求，发起批处理解析
    List<BatchFileRequest> requests = new ArrayList<>();
    requests.add(new BatchFileRequest("A客户单据.xlsx", INBOUND_ID_A, new FileInputStream(FILE_A)));
    requests.add(new BatchFileRequest("B客户单据.xlsx", INBOUND_ID_B, new FileInputStream(FILE_B)));

    System.out.println(">>> 1. 开始多客户文件并行/批处理解析...");
    Map<String, ParseResult> resultMap = appService.parseBatch(requests);

    // 3. 验证跨文件防重机制
    System.out.println("\n>>> 2. 检查跨文件防重校验错题本：");
    for (Map.Entry<String, ParseResult> entry : resultMap.entrySet()) {
      System.out.println("【" + entry.getKey() + "】包含错误数量：" + entry.getValue().errors().size());
      entry.getValue().errors().forEach(err -> System.out.println("  └─ " + err.message()));
    }

    // 4. 提取成功的 JSON，模拟业务融合后导出
    // (省略 JSON 合并细节，假设我们取了A文件的合法JSON，补充了后台属性)
    String aJson = resultMap.get("A客户单据.xlsx").jsonPayload();
    String enhancedJson = aJson.replace("}", ",\"sysStatus\":\"校验通过\"}");

    System.out.println("\n>>> 3. 使用统一标准模板导出结果...");
    try (FileOutputStream fos = new FileOutputStream(MERGED_OUT_FILE)) {
      // 注意：我们依然传的是输入模板ID，引擎会自动去找到挂载的那个标准输出模板
      appService.exportExcel(INBOUND_ID_A, enhancedJson, fos);
    }
    System.out.println("已生成标准输出表单: " + MERGED_OUT_FILE);
  }

  private void generateMockFiles() {
    // Client A 的表单格式 (A:姓名, B:证件)
    List<List<Object>> sheetA = new ArrayList<>();
    sheetA.add(List.of("姓名", "证件"));
    sheetA.add(List.of("李四", "11111"));
    sheetA.add(List.of("张三", "99999")); // <--- 注意这个张三
    EasyExcel.write(FILE_A).sheet("Sheet1").doWrite(sheetA);

    // Client B 的表单格式 (A:证件, B:空, C:姓名)
    List<List<Object>> sheetB = new ArrayList<>();
    sheetB.add(List.of("证件号", "乱七八糟的字段", "申请人姓名"));
    sheetB.add(List.of("22222", "xx", "王五"));
    sheetB.add(List.of("99999", "xx", "张三")); // <--- 跨文件重复的张三！
    EasyExcel.write(FILE_B).sheet("Sheet1").doWrite(sheetB);
  }
}
