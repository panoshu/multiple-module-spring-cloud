package com.example.file.domain.service;

import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.CanonicalModelDef;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.SplitConfig;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.types.BizType;
import com.example.file.types.TemplateCode;
import com.example.file.types.TemplateConfigId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTemplateIdentifierTest {

  @Test
  void should_identify_template_by_fingerprint_match() {
    SourceTemplateDef tplA = createTemplate("tpl-a", IdentifyMode.AUTO,
        List.of("企业名称", "申报日期", "商品编码"));
    SourceTemplateDef tplB = createTemplate("tpl-b", IdentifyMode.AUTO,
        List.of("客户姓名", "身份证号", "联系电话"));
    TemplateConfig config = createConfig(List.of(tplA, tplB));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "企业名称", 1, "ABC公司"), false),
        RawRow.of(1, Map.of(0, "申报日期", 1, "2026-07-18"), false),
        RawRow.of(2, Map.of(0, "商品编码", 1, "商品名称", 2, "数量"), false)));

    SourceTemplateIdentifier identifier = new SourceTemplateIdentifier();
    Optional<SourceTemplateDef> matched = identifier.identify(config, stream);

    assertThat(matched).isPresent();
    assertThat(matched.get().id().value()).isEqualTo("tpl-a");
  }

  @Test
  void should_return_empty_when_no_fingerprint_match() {
    SourceTemplateDef tpl = createTemplate("tpl-x", IdentifyMode.AUTO,
        List.of("完全不相关", "的字段名"));
    TemplateConfig config = createConfig(List.of(tpl));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "企业名称", 1, "ABC公司"), false)));

    SourceTemplateIdentifier identifier = new SourceTemplateIdentifier();
    Optional<SourceTemplateDef> matched = identifier.identify(config, stream);

    assertThat(matched).isEmpty();
  }

  @Test
  void should_skip_manual_templates() {
    SourceTemplateDef manual = createTemplate("tpl-m", IdentifyMode.MANUAL,
        List.of("企业名称"));
    TemplateConfig config = createConfig(List.of(manual));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "企业名称", 1, "ABC公司"), false)));

    SourceTemplateIdentifier identifier = new SourceTemplateIdentifier();
    Optional<SourceTemplateDef> matched = identifier.identify(config, stream);

    assertThat(matched).isEmpty();
  }

  private SourceTemplateDef createTemplate(String code, IdentifyMode mode, List<String> fingerprint) {
    List<RegionDef> regions = List.of(
        new RegionDef("basic", RegionType.KEY_VALUE, "properties", null,
            new KvStrategy(KvValuePosition.RIGHT, Map.of(), 3))
    );
    return new SourceTemplateDef(new TemplateCode(code), mode, fingerprint, regions, UserNo.of("test"));
  }

  private TemplateConfig createConfig(List<SourceTemplateDef> templates) {
    return TemplateConfig.create(
        new TemplateConfigId("cfg-001"),
        new BizType("TEST"),
        "V1",
        ErrorPolicy.COLLECT_ALL,
        new CanonicalModelDef(List.of(), List.of()),
        List.of(),
        List.of(),
        new SplitConfig(List.of(), null, null, null, false),
        templates,
        UserNo.of("test")
    );
  }

  static class FakeStream implements RawRowStream {
    private final List<RawRow> rows;
    private int idx = 0;
    FakeStream(List<RawRow> rows) { this.rows = rows; }
    @Override public boolean hasNext() { return idx < rows.size(); }
    @Override public RawRow next() { return rows.get(idx++); }
    @Override public RawRow peek() { return idx < rows.size() ? rows.get(idx) : rows.get(rows.size() - 1); }
    @Override public int currentRowIndex() { return idx == 0 ? -1 : rows.get(idx - 1).rowIndex(); }
  }
}
