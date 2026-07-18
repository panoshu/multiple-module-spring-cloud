package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.valueobject.config.CanonicalModelDef;
import com.example.file.domain.model.valueobject.config.DerivationRule;
import com.example.file.domain.model.valueobject.config.SplitConfig;
import com.example.file.domain.model.valueobject.config.ValidationRule;
import com.example.file.types.BizType;
import com.example.file.types.TemplateConfigId;
import com.example.shared.primitives.identity.UserNo;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class YamlConfigLoader implements ConfigLoader {

  @Override
  public TemplateConfig loadFromYaml(BizType bizType, String baselineYaml,
                                     List<String> sourceTemplateYamls, String version,
                                     UserNo operator) {
    Yaml yaml = new Yaml();

    Map<String, Object> baseline = yaml.load(baselineYaml);

    List<Map<String, Object>> sourceTemplates = new ArrayList<>();
    for (String sourceYaml : sourceTemplateYamls) {
      Map<String, Object> source = yaml.load(sourceYaml);
      sourceTemplates.add(source);
    }

    return buildTemplateConfig(bizType, baseline, sourceTemplates, version, operator);
  }

  @SuppressWarnings("unchecked")
  private TemplateConfig buildTemplateConfig(BizType bizType, Map<String, Object> baseline,
                                             List<Map<String, Object>> sourceTemplates,
                                             String version, UserNo operator) {
    TemplateConfigId id = TemplateConfigId.of(generateId());

    ErrorPolicy errorPolicy = ErrorPolicy.FAIL_FAST;
    if (baseline != null && baseline.get("errorPolicy") != null) {
      try {
        errorPolicy = ErrorPolicy.valueOf((String) baseline.get("errorPolicy"));
      } catch (Exception ignored) {
      }
    }

    CanonicalModelDef canonicalModel = new CanonicalModelDef(List.of(), List.of());

    List<ValidationRule> validationRules = List.of();

    List<DerivationRule> derivationRules = List.of();

    SplitConfig splitConfig = new SplitConfig(
        List.of(), null, null, null, null, false, 0
    );

    List<SourceTemplateDef> sourceTemplateDefs = List.of();

    return TemplateConfig.create(
        id, bizType, version, errorPolicy, canonicalModel,
        validationRules, derivationRules, splitConfig,
        sourceTemplateDefs, operator
    );
  }

  private String generateId() {
    return "01HW-" + System.currentTimeMillis();
  }
}
