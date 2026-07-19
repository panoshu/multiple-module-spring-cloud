package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.types.BizType;
import com.example.file.types.TemplateCode;
import com.example.file.types.TemplateConfigId;
import com.example.shared.primitives.identity.UserNo;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.util.*;

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

    ErrorPolicy errorPolicy = parseEnum(baseline, "errorPolicy", ErrorPolicy.class, ErrorPolicy.FAIL_FAST);

    CanonicalModelDef canonicalModel = parseCanonicalModel(baseline);

    List<ValidationRule> validationRules = parseValidationRules(baseline);

    List<DerivationRule> derivationRules = parseDerivationRules(baseline);

    SplitConfig splitConfig = parseSplitConfig(baseline);

    List<SourceTemplateDef> sourceTemplateDefs = new ArrayList<>();
    for (Map<String, Object> st : sourceTemplates) {
      sourceTemplateDefs.add(parseSourceTemplate(st, operator));
    }

    return TemplateConfig.create(
        id, bizType, version, errorPolicy, canonicalModel,
        validationRules, derivationRules, splitConfig,
        sourceTemplateDefs, operator
    );
  }

  @SuppressWarnings("unchecked")
  private CanonicalModelDef parseCanonicalModel(Map<String, Object> baseline) {
    Map<String, Object> cm = (Map<String, Object>) baseline.get("canonicalModel");
    if (cm == null) return new CanonicalModelDef(List.of(), List.of());

    List<PropertyFieldDef> properties = new ArrayList<>();
    List<Map<String, Object>> props = (List<Map<String, Object>>) cm.getOrDefault("properties", List.of());
    for (Map<String, Object> p : props) {
      properties.add(new PropertyFieldDef(
          (String) p.get("code"),
          parseEnum(p, "type", FieldType.class, FieldType.STRING),
          Boolean.TRUE.equals(p.get("required")),
          (String) p.get("pattern")));
    }

    List<TableDef> tables = new ArrayList<>();
    List<Map<String, Object>> tbls = (List<Map<String, Object>>) cm.getOrDefault("tables", List.of());
    for (Map<String, Object> t : tbls) {
      List<FieldDef> fields = new ArrayList<>();
      List<Map<String, Object>> flds = (List<Map<String, Object>>) t.getOrDefault("fields", List.of());
      for (Map<String, Object> f : flds) {
        Object scaleObj = f.get("scale");
        Integer scale = scaleObj instanceof Number ? ((Number) scaleObj).intValue() : null;
        fields.add(new FieldDef(
            (String) f.get("code"),
            parseEnum(f, "type", FieldType.class, FieldType.STRING),
            Boolean.TRUE.equals(f.get("required")),
            scale));
      }
      tables.add(new TableDef((String) t.get("code"), fields));
    }

    return new CanonicalModelDef(properties, tables);
  }

  @SuppressWarnings("unchecked")
  private List<ValidationRule> parseValidationRules(Map<String, Object> baseline) {
    List<ValidationRule> rules = new ArrayList<>();
    List<Map<String, Object>> list = (List<Map<String, Object>>) baseline.getOrDefault("validationRules", List.of());
    for (Map<String, Object> r : list) {
      rules.add(new ValidationRule(
          (String) r.get("field"),
          parseEnum(r, "scope", ValidationScope.class, ValidationScope.ROW),
          (String) r.get("expr"),
          (String) r.get("message"),
          parseEnum(r, "type", FieldType.class, FieldType.STRING)));
    }
    return rules;
  }

  @SuppressWarnings("unchecked")
  private List<DerivationRule> parseDerivationRules(Map<String, Object> baseline) {
    List<DerivationRule> rules = new ArrayList<>();
    List<Map<String, Object>> list = (List<Map<String, Object>>) baseline.getOrDefault("derivationRules", List.of());
    for (Map<String, Object> r : list) {
      rules.add(new DerivationRule(
          (String) r.get("field"),
          (String) r.get("expr"),
          parseEnum(r, "type", FieldType.class, FieldType.STRING),
          (String) r.get("description")));
    }
    return rules;
  }

  @SuppressWarnings("unchecked")
  private SplitConfig parseSplitConfig(Map<String, Object> baseline) {
    Map<String, Object> sc = (Map<String, Object>) baseline.get("splitConfig");
    if (sc == null) return new SplitConfig(List.of(), null, SplitMissPolicy.ERROR, null, null, false, 0);

    List<String> keys = (List<String>) sc.getOrDefault("keys", List.of());

    Map<String, Object> sk = (Map<String, Object>) sc.get("splitKey");
    SplitKeyDef splitKey = null;
    if (sk != null) {
      splitKey = new SplitKeyDef(
          (String) sk.get("targetField"),
          (String) sk.get("sourcePath"),
          parseEnum(sk, "type", SplitKeyType.class, SplitKeyType.FIELD_VALUE));
    }

    SplitMissPolicy onMiss = parseEnum(sc, "onMiss", SplitMissPolicy.class, SplitMissPolicy.ERROR);
    Object maxRows = sc.get("maxRowsPerSubTask");
    int maxRowsPerSubTask = maxRows instanceof Number ? ((Number) maxRows).intValue() : 0;

    return new SplitConfig(keys, splitKey, onMiss,
        (String) sc.get("defaultOnMissValue"),
        (String) sc.get("fileNamingTemplate"),
        Boolean.TRUE.equals(sc.get("promoteToContext")),
        maxRowsPerSubTask);
  }

  @SuppressWarnings("unchecked")
  private SourceTemplateDef parseSourceTemplate(Map<String, Object> st, UserNo operator) {
    TemplateCode code = TemplateCode.of((String) st.get("id"));
    IdentifyMode mode = parseEnum(st, "identifyMode", IdentifyMode.class, IdentifyMode.AUTO);
    List<String> fingerprint = (List<String>) st.getOrDefault("fingerprint", List.of());

    List<RegionDef> regions = new ArrayList<>();
    List<Map<String, Object>> regionList = (List<Map<String, Object>>) st.getOrDefault("regions", List.of());
    for (Map<String, Object> r : regionList) {
      regions.add(parseRegion(r));
    }

    return new SourceTemplateDef(code, mode, fingerprint, regions, operator);
  }

  @SuppressWarnings("unchecked")
  private RegionDef parseRegion(Map<String, Object> r) {
    String name = (String) r.get("name");
    RegionType type = parseEnum(r, "type", RegionType.class, RegionType.KEY_VALUE);
    String bindTo = (String) r.get("bindTo");

    RegionTrigger trigger = null;
    Map<String, Object> tr = (Map<String, Object>) r.get("trigger");
    if (tr != null) {
      trigger = new RegionTrigger(
          parseEnum(tr, "matchType", TriggerMatchType.class, TriggerMatchType.HEADER_SNIFF),
          tr.get("minMatchCount") instanceof Number ? ((Number) tr.get("minMatchCount")).intValue() : 1);
    }

    Map<String, Object> stratMap = (Map<String, Object>) r.get("strategy");
    RegionStrategy strategy = parseStrategy(stratMap, type);

    return new RegionDef(name, type, bindTo, trigger, strategy);
  }

  @SuppressWarnings("unchecked")
  private RegionStrategy parseStrategy(Map<String, Object> stratMap, RegionType type) {
    if (stratMap == null) return null;

    if (type == RegionType.KEY_VALUE) {
      Map<String, List<String>> labelAliases = new LinkedHashMap<>();
      Map<String, Object> la = (Map<String, Object>) stratMap.get("labelAliases");
      if (la != null) {
        la.forEach((k, v) -> labelAliases.put(k, (List<String>) v));
      }
      return new KvStrategy(
          parseEnum(stratMap, "valuePosition", KvValuePosition.class, KvValuePosition.RIGHT),
          labelAliases,
          stratMap.get("maxBlankRows") instanceof Number ? ((Number) stratMap.get("maxBlankRows")).intValue() : 3);
    } else if (type == RegionType.TABLE) {
      Map<String, List<String>> headerAliases = new LinkedHashMap<>();
      Map<String, Object> ha = (Map<String, Object>) stratMap.get("headerAliases");
      if (ha != null) {
        ha.forEach((k, v) -> headerAliases.put(k, (List<String>) v));
      }
      DataEndRule dataEnd = null;
      Map<String, Object> de = (Map<String, Object>) stratMap.get("dataEnd");
      if (de != null) {
        dataEnd = new DataEndRule(
            (List<String>) de.getOrDefault("markers", List.of()),
            de.get("blankRowCount") instanceof Number ? ((Number) de.get("blankRowCount")).intValue() : 0);
      }
      return new TableStrategy(
          stratMap.get("headerRows") instanceof Number ? ((Number) stratMap.get("headerRows")).intValue() : 1,
          stratMap.get("headerNameRow") instanceof Number ? ((Number) stratMap.get("headerNameRow")).intValue() : 0,
          parseEnum(stratMap, "matchBy", TableMatchBy.class, TableMatchBy.HEADER_NAME),
          headerAliases,
          parseEnum(stratMap, "headerMatching", HeaderMatching.class, HeaderMatching.STRICT),
          stratMap.get("maxRows") instanceof Number ? ((Number) stratMap.get("maxRows")).intValue() : 0,
          dataEnd);
    }
    return null;
  }

  private <E extends Enum<E>> E parseEnum(Map<String, Object> map, String key, Class<E> enumClass, E defaultVal) {
    Object val = map.get(key);
    if (val == null) return defaultVal;
    try {
      return Enum.valueOf(enumClass, val.toString());
    } catch (Exception e) {
      return defaultVal;
    }
  }

  private String generateId() {
    return "01HW-" + System.currentTimeMillis();
  }
}
