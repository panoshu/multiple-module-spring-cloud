package com.example.share.logging.obfuscate.filter;

import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ValidatedFieldConfig;
import com.example.share.logging.obfuscate.service.ValueObfuscate;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.BodyFilter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class JsonBodyObfuscationFilter implements BodyFilter {

  private final Map<String, ValidatedFieldConfig> jsonPathRules;
  private final ValueObfuscate valueObfuscate;
  private final boolean enabled;

  private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
    .jsonProvider(new JacksonJsonProvider()) // 使用 Jackson
    .mappingProvider(new JacksonMappingProvider())
    .options(Option.SUPPRESS_EXCEPTIONS) // 抑制路径未找到异常
    .build();

  public JsonBodyObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    this.jsonPathRules = config.getJsonPathRules();
    this.valueObfuscate = valueObfuscate;
    this.enabled = config.getGlobalConfig().enable();

    log.info("Initialized JSON Body Filter with {} rules", jsonPathRules.size());
  }

  @Override
  public String filter(String contentType, @Nonnull String body) {
    if (!enabled || !isJson(contentType) || jsonPathRules.isEmpty() || body.isBlank()) {
      return body;
    }

    long startTime = System.nanoTime();
    AtomicInteger matchCount = new AtomicInteger(0);

    try {
      DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);

      for (Map.Entry<String, ValidatedFieldConfig> entry : jsonPathRules.entrySet()) {
        String path = entry.getKey();
        ValidatedFieldConfig fieldConfig = entry.getValue();

        context.map(path, (currentValue, config) -> {
          if (currentValue == null) return null;

          // 记录命中详情 (Debug 级别)
          if (log.isDebugEnabled()) {
            log.debug("JsonPath matched: [{}], Strategy: [{}]", path, fieldConfig.strategy());
          }
          matchCount.incrementAndGet();

          return valueObfuscate.obfuscate(currentValue.toString(), fieldConfig);
        });
      }

      // 如果有修改，才进行序列化
      if (matchCount.get() > 0) {
        String result = context.jsonString();
        recordPerformance(startTime, body.length(), matchCount.get());
        return result;
      }

      return body;

    } catch (Exception e) {
      // 记录解析失败，这通常意味着 Body 不是有效的 JSON，或者太大了
      log.warn("JSON obfuscation skipped. Reason: {}. Body preview: {}",
        e.getMessage(), body.substring(0, Math.min(body.length(), 100)));
      return body;
    }
  }

  private void recordPerformance(long startTime, int bodyLength, int matches) {
    if (log.isDebugEnabled()) {
      long duration = (System.nanoTime() - startTime) / 1000; // 微秒
      log.debug("JSON Obfuscation completed. Matches: {}, BodySize: {} chars, Cost: {} us",
        matches, bodyLength, duration);
    }
  }

  private boolean isJson(String contentType) {
    return contentType != null && (contentType.contains("json") || contentType.contains("JSON"));
  }
}
