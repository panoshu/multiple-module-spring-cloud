package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.types.BizType;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigLoaderTest {

  private static final String BASELINE_YAML = """
    bizType: ENTERPRISE_PLAN
    version: "1.0"
    errorPolicy: COLLECT_ALL

    canonicalModel:
      properties:
        - { code: planNo, type: STRING, required: true }
        - { code: planName, type: STRING, required: true }
        - { code: customerNo, type: STRING, required: true }
        - { code: customerName, type: STRING, required: true }
        - { code: filler, type: STRING }
        - { code: reviewer, type: STRING }
      tables:
        - code: employees
          fields:
            - { code: seq, type: INTEGER, required: true }
            - { code: name, type: STRING, required: true }
            - { code: idType, type: STRING, required: true }
            - { code: idNo, type: STRING, required: true }

    validationRules:
      - { field: idNo, scope: ROW, expr: "idNo != null", message: "证件编号不能为空", type: STRING }
      - { field: name, scope: ROW, expr: "name != null", message: "姓名不能为空", type: STRING }

    derivationRules: []

    splitConfig:
      keys: [customerNo]
      splitKey:
        targetField: customerNo
        sourcePath: employees.customerNo
        type: FIELD_VALUE
      onMiss: ERROR
      maxRowsPerSubTask: 1000
    """;
  private static final String SOURCE_TEMPLATE_YAML = """
    id: STANDARD_TEMPLATE
    name: "企业计划标准模板"
    identifyMode: AUTO
    fingerprint: ["企业计划编号：", "企业客户号：", "序号*"]
    regions:
      - name: basic_info
        type: KEY_VALUE
        bindTo: properties
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 2 }
        strategy:
          valuePosition: RIGHT
          labelAliases:
            planNo: ["企业计划编号："]
            planName: ["企业计划名称："]
            customerNo: ["企业客户号："]
            customerName: ["企业客户名称："]
          maxBlankRows: 2
      - name: employee_list
        type: TABLE
        bindTo: employees
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 5 }
        strategy:
          headerRows: 3
          headerNameRow: 1
          headerAliases:
            seq: [XH]
            name: [XM]
            idType: [ZJLX]
            idNo: [ZJHM]
          dataEnd: { markers: ["结束"], blankRowCount: 1 }
      - name: filler_info
        type: KEY_VALUE
        bindTo: properties
        trigger: { matchType: HEADER_SNIFF, minMatchCount: 1 }
        strategy:
          valuePosition: RIGHT
          labelAliases:
            filler: ["填表人:"]
            reviewer: ["复核人："]
          maxBlankRows: 1
    """;
  private final ConfigLoader loader = new YamlConfigLoader();

  @Test
  void loadFromYaml_基础字段() {
    TemplateConfig config = loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
      List.of(SOURCE_TEMPLATE_YAML), "1.0",
      UserNo.of("test-user"));

    assertThat(config.bizType().value()).isEqualTo("ENTERPRISE_PLAN");
    assertThat(config.templateVersion()).isEqualTo("1.0");
    assertThat(config.errorPolicy().name()).isEqualTo("COLLECT_ALL");
  }

  @Test
  void loadFromYaml_规范模型() {
    TemplateConfig config = loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
      List.of(SOURCE_TEMPLATE_YAML), "1.0",
      UserNo.of("test-user"));

    assertThat(config.canonicalModel().properties()).hasSize(6);
    assertThat(config.canonicalModel().tables()).hasSize(1);
    assertThat(config.canonicalModel().tables().get(0).code()).isEqualTo("employees");
  }

  @Test
  void loadFromYaml_校验规则() {
    TemplateConfig config = loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
      List.of(SOURCE_TEMPLATE_YAML), "1.0",
      UserNo.of("test-user"));

    assertThat(config.validationRules()).hasSize(2);
    assertThat(config.validationRules().get(0).field()).isEqualTo("idNo");
    assertThat(config.validationRules().get(0).expr()).isEqualTo("idNo != null");
  }

  @Test
  void loadFromYaml_拆分配置() {
    TemplateConfig config = loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
      List.of(SOURCE_TEMPLATE_YAML), "1.0",
      UserNo.of("test-user"));

    assertThat(config.splitConfig().keys()).contains("customerNo");
    assertThat(config.splitConfig().splitKey().sourcePath()).isEqualTo("employees.customerNo");
    assertThat(config.splitConfig().maxRowsPerSubTask()).isEqualTo(1000);
  }

  @Test
  void loadFromYaml_源模板() {
    TemplateConfig config = loader.loadFromYaml(
      BizType.of("ENTERPRISE_PLAN"), BASELINE_YAML,
      List.of(SOURCE_TEMPLATE_YAML), "1.0",
      UserNo.of("test-user"));

    assertThat(config.sourceTemplates()).hasSize(1);
    assertThat(config.sourceTemplates().get(0).id().value()).isEqualTo("STANDARD_TEMPLATE");
    assertThat(config.sourceTemplates().get(0).regions()).hasSize(3);
  }
}
