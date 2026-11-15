package com.example.file.infrastructure.converter;

import com.example.file.domain.model.enums.IdentifyMode;
import com.example.file.domain.model.parse.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.parse.valueobject.config.KvStrategy;
import com.example.file.domain.model.parse.valueobject.config.RegionDef;
import com.example.file.domain.model.parse.valueobject.config.RegionStrategy;
import com.example.file.domain.model.parse.valueobject.config.TableStrategy;
import com.example.file.types.TemplateCode;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TemplateConfig JSON 持久化工具.
 *
 * <p>领域模型不依赖 Jackson：{@link SourceTemplateDef} 等非 record 实体在这里
 * 转换为稳定的 Map 契约；{@link RegionStrategy} 是 sealed 接口，通过 mix-in
 * 声明多态类型信息，保证序列化后能还原为 {@link KvStrategy}/{@link TableStrategy}.</p>
 *
 * @author file-service
 */
public final class TemplateConfigJson {

  public static final ObjectMapper MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .addMixIn(RegionStrategy.class, RegionStrategyMixIn.class)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

  private TemplateConfigJson() {
  }

  public static String sourceTemplatesToJson(List<SourceTemplateDef> templates) {
    if (templates == null || templates.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> payload = new ArrayList<>(templates.size());
    for (SourceTemplateDef template : templates) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", template.id().value());
      item.put("identifyMode", template.identifyMode().name());
      item.put("fingerprint", template.fingerprint());
      item.put("regions", template.regions());
      payload.add(item);
    }
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化源模板列表失败", e);
    }
  }

  public static List<SourceTemplateDef> sourceTemplatesFromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<Map<String, Object>> payload = MAPPER.readValue(
        json, new TypeReference<List<Map<String, Object>>>() {
        });
      List<SourceTemplateDef> result = new ArrayList<>(payload.size());
      for (Map<String, Object> item : payload) {
        String id = (String) item.get("id");
        IdentifyMode mode = IdentifyMode.valueOf((String) item.get("identifyMode"));
        @SuppressWarnings("unchecked")
        List<String> fingerprint = (List<String>) item.getOrDefault("fingerprint", List.of());
        List<RegionDef> regions = MAPPER.convertValue(
          item.get("regions"), new TypeReference<List<RegionDef>>() {
          });
        result.add(new SourceTemplateDef(
          TemplateCode.of(id), mode, fingerprint, regions, "SYSTEM"));
      }
      return result;
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new IllegalStateException("反序列化源模板列表失败", e);
    }
  }

  /**
   * JavaTimeModule + sealed interface 多态 mix-in.
   */
  @JsonTypeInfo(
    use = JsonTypeInfo.Id.SIMPLE_NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "strategyType")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = KvStrategy.class, name = "KvStrategy"),
    @JsonSubTypes.Type(value = TableStrategy.class, name = "TableStrategy")
  })
  abstract static class RegionStrategyMixIn {
  }
}
