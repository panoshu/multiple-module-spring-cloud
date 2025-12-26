package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.BodyFilter;

import java.util.List;
import java.util.Map;

@Slf4j
public class JsonBodyObfuscationFilter extends BaseObfuscationFilter implements BodyFilter {

  private final Map<String, ValidatedFieldConfig> jsonPathRules;

  private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
    .jsonProvider(new JacksonJsonProvider())
    .mappingProvider(new JacksonMappingProvider())
    .options(com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS)
    .options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
    .build();

  public JsonBodyObfuscationFilter(ObfuscateConfig config,
                                   ObfuscationStrategyFactory strategyFactory) {
    super(config, strategyFactory);
    this.jsonPathRules = config.getJsonPathRules();
    log.debug("Initialized JSON body obfuscation with {} rules", jsonPathRules.size());
  }

  @Override
  public String filter(String contentType, @Nonnull String body) {
    if (!this.config.getGlobalConfig().enable() || !isJsonContentType(contentType) || jsonPathRules.isEmpty()) {
      return body;
    }

    try {
      return obfuscateJsonBody(body);
    } catch (Exception e) {
      log.error("Failed to obfuscate JSON body: {}", e.getMessage(), e);
      return body;
    }
  }

  private String obfuscateJsonBody(String body) {
    DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);
    boolean modified = false;

    for (Map.Entry<String, ValidatedFieldConfig> entry : jsonPathRules.entrySet()) {
      String jsonPath = entry.getKey();
      ValidatedFieldConfig fieldConfig = entry.getValue();

      try {
        List<?> values = context.read(jsonPath);
        if (values != null && !values.isEmpty()) {
          boolean pathModified = false;
          for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value != null) {
              String original = extractStringValue(value);
              String obfuscated = obfuscateValue(original, fieldConfig);
              if (!original.equals(obfuscated)) {
                context.set(jsonPath + "[" + i + "]", obfuscated);
                pathModified = true;
              }
            }
          }
          if (pathModified) modified = true;
        }
      } catch (PathNotFoundException ignored) {
        // 路径不存在，忽略
      } catch (Exception e) {
        log.warn("Failed to obfuscate field at path '{}': {}", jsonPath, e.getMessage());
      }
    }

    return modified ? context.jsonString() : body;
  }

  private String extractStringValue(Object value) {
    if (value instanceof JsonNode jsonNode) {
      return jsonNode.isTextual() ? jsonNode.textValue() : jsonNode.toString();
    }
    return value.toString();
  }

  private boolean isJsonContentType(String contentType) {
    if (contentType == null) return false;
    String lower = contentType.toLowerCase();
    return lower.contains("application/json") ||
      lower.contains("text/json") ||
      lower.contains("+json");
  }
}
