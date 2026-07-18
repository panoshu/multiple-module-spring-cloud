package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.enums.ConfigStatus;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.enums.SplitMissPolicy;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.types.BizType;
import com.example.file.types.TemplateCode;
import com.example.file.types.TemplateConfigId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateConfigTest {

  @Test
  void should_create_draft_config_and_activate() {
    TemplateConfig config = newConfig();
    assertThat(config.status()).isEqualTo(ConfigStatus.DRAFT);
    config.activate();
    assertThat(config.status()).isEqualTo(ConfigStatus.ACTIVE);
  }

  @Test
  void findSourceTemplate_should_match_by_code() {
    TemplateConfig config = newConfig();
    Optional<SourceTemplateDef> found = config.findSourceTemplate(TemplateCode.of("CUST_A_V2"));
    assertThat(found).isPresent();
  }

  @Test
  void autoIdentify_should_match_fingerprint_headers() {
    TemplateConfig config = newConfig();
    Optional<SourceTemplateDef> found = config.autoIdentify(List.of("申报单位", "申报日期", "明细列表"));
    assertThat(found).isPresent();
  }

  private TemplateConfig newConfig() {
    CanonicalModelDef model = new CanonicalModelDef(
        List.of(new PropertyFieldDef("enterpriseName", FieldType.STRING, true, null)),
        List.of(new TableDef("detailList", List.of(new FieldDef("itemNo", FieldType.STRING, true, null))))
    );
    SplitConfig split = new SplitConfig(List.of("detailList.deptCode"),
        null, SplitMissPolicy.ERROR, null, null, true, 0);
    RegionDef region = new RegionDef("kv_basic", RegionType.KEY_VALUE, "properties",
        null, new KvStrategy(KvValuePosition.RIGHT, java.util.Map.of(), 3));
    SourceTemplateDef source = new SourceTemplateDef(TemplateCode.of("CUST_A_V2"),
        IdentifyMode.AUTO, List.of("申报单位", "申报日期", "明细列表"),
        List.of(region), UserNo.of("u1"));
    return TemplateConfig.create(TemplateConfigId.of("cfg1"), BizType.of("import_declare"),
        "v1", ErrorPolicy.COLLECT_ALL, model, List.of(), List.of(), split,
        List.of(source), UserNo.of("u1"));
  }
}
