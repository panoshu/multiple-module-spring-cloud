package com.example.file.infrastructure;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.parse.aggregate.TemplateConfig;
import com.example.file.domain.model.parse.valueobject.CanonicalData;
import com.example.file.domain.model.parse.valueobject.RawRowStream;
import com.example.file.domain.model.parse.valueobject.RegionParseResult;
import com.example.file.domain.model.parse.valueobject.SplitUnit;
import com.example.file.domain.model.parse.valueobject.config.RegionDef;
import com.example.file.domain.service.CanonicalModelBuilder;
import com.example.file.domain.service.KeyValueRegionParser;
import com.example.file.domain.service.ParseContext;
import com.example.file.domain.service.RegionStateMachine;
import com.example.file.domain.service.TableRegionParser;
import com.example.file.domain.service.TaskSplitter;
import com.example.file.infrastructure.gateway.ExcelParserImpl;
import com.example.file.infrastructure.gateway.YamlConfigLoader;
import com.example.file.types.BizType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 年金 FORM_DETAIL 模板（本地播种配置）与示例 Excel 的契约测试.
 *
 * <p>覆盖：YAML 加载 → 源模板自动识别 → 区域状态机解析 → canonical 构建 → 按计划号拆分.
 * 该测试保证本地播种模板与 {@code docs/excel/annuity_form_detail.xlsx} 始终一致，
 * 避免联调时才发现模板指纹/表头映射漂移.</p>
 *
 * @author file-service
 */
@ExtendWith(ExcelFixtureExtension.class)
class AnnuityFormDetailTemplateParseTest {

  private static final Path BASELINE =
    Path.of("file-service", "file-infrastructure", "src", "main", "resources", "templates", "form_detail_base.yaml");
  private static final Path SOURCE_TEMPLATE =
    Path.of("file-service", "file-infrastructure", "src", "main", "resources", "templates", "form_detail_source_annuity.yaml");
  private static final Path EXCEL = Path.of("docs", "excel", "annuity_form_detail.xlsx");

  @Test
  @DisplayName("播种模板可识别并解析年金员工明细示例表单")
  void parsesAnnuityFormDetailExcel() throws Exception {
    TemplateConfig config = loadConfig();
    List<RegionDef> regions = config.sourceTemplates().get(0).regions();

    CanonicalData data = parseExcel(EXCEL, regions);

    assertThat(data.properties())
      .containsEntry("planNo", "PLAN-2026-001")
      .containsEntry("customerNo", "CUST-001")
      .containsEntry("customerName", "示例客户");
    assertThat(data.tables()).containsKey("employees");
    assertThat(data.tables().get("employees")).hasSize(2);
    assertThat(data.tables().get("employees").get(0))
      .containsEntry("employeeName", "张三")
      .containsEntry("idCardNo", "110101199001010011")
      .containsEntry("age", "30")
      .containsEntry("monthlySalary", "2000000")
      .containsEntry("monthlyContribution", "200000");
  }

  @Test
  @DisplayName("按 planNo 拆分出单个 subTask，splitKey 为计划号")
  void splitsByPlanNo() throws Exception {
    TemplateConfig config = loadConfig();
    CanonicalData data = parseExcel(EXCEL, config.sourceTemplates().get(0).regions());

    Map<String, Object> flat = new LinkedHashMap<>(data.properties());
    flat.putAll(data.tables());
    List<SplitUnit> units = new TaskSplitter().split(flat, config.splitConfig());

    assertThat(units).hasSize(1);
    assertThat(units.get(0).splitKey()).isEqualTo("PLAN-2026-001");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) units.get(0).data().get("employees");
    assertThat(rows).hasSize(2);
  }

  @Test
  @DisplayName("源模板可 JSON round-trip（TemplateConfig 持久化契约）")
  void sourceTemplateDefJsonRoundTrip() throws Exception {
    TemplateConfig config = loadConfig();

    String json = com.example.file.infrastructure.converter.TemplateConfigJson
      .sourceTemplatesToJson(config.sourceTemplates());
    List<com.example.file.domain.model.parse.aggregate.entity.SourceTemplateDef> restored =
      com.example.file.infrastructure.converter.TemplateConfigJson.sourceTemplatesFromJson(json);

    assertThat(restored).hasSize(1);
    assertThat(restored.get(0).id().value()).isEqualTo("FORM_DETAIL_ANNUITY_STANDARD");
    assertThat(restored.get(0).regions()).hasSize(2);
    assertThat(restored.get(0).regions().get(1).strategy())
      .isInstanceOf(com.example.file.domain.model.parse.valueobject.config.TableStrategy.class);
  }

  private TemplateConfig loadConfig() throws Exception {
    String baseline = Files.readString(BASELINE);
    String source = Files.readString(SOURCE_TEMPLATE);
    ConfigLoader loader = new YamlConfigLoader(new StubIdService());
    return loader.loadFromYaml(BizType.of("FORM_DETAIL"), baseline, List.of(source), "1.0", "test");
  }

  private CanonicalData parseExcel(Path path, List<RegionDef> regions) throws Exception {
    ExcelParser parser = new ExcelParserImpl();
    RegionStateMachine stateMachine = new RegionStateMachine(Map.of(
      RegionType.KEY_VALUE, new KeyValueRegionParser(),
      RegionType.TABLE, new TableRegionParser()));
    try (InputStream in = new FileInputStream(path.toFile())) {
      RawRowStream stream = parser.openStream(in);
      List<RegionParseResult> results = stateMachine.drive(stream, regions, new ParseContext(regions));
      return new CanonicalModelBuilder().build(results, regions);
    }
  }

  private static final class StubIdService implements com.example.shared.identifier.contract.IdService {
    @Override
    public <T extends com.example.shared.identifier.contract.Identifier<?>> T nextId(Class<T> type) {
      return instantiate(type, "01H8ANNUITYTEMPLATE");
    }

    @Override
    public <T extends com.example.shared.identifier.contract.Identifier<?>> T nextId(Class<T> type, String bizTag) {
      return nextId(type);
    }

    @Override
    public String nextId(com.example.shared.identifier.contract.IdType idType) {
      return "01H8ANNUITYTEMPLATE";
    }

    @Override
    public String nextId(com.example.shared.identifier.contract.IdType idType, String bizTag) {
      return "01H8ANNUITYTEMPLATE";
    }

    @Override
    public <T extends com.example.shared.identifier.contract.Identifier<Long>> T nextLongId(Class<T> type, String bizTag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Long nextLongId(String bizTag) {
      throw new UnsupportedOperationException();
    }

    private static <T> T instantiate(Class<T> type, String value) {
      try {
        return type.getDeclaredConstructor(String.class).newInstance(value);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
