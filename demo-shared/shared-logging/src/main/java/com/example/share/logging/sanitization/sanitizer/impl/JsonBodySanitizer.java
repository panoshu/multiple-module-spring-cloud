package com.example.share.logging.sanitization.sanitizer.impl;

import com.example.share.logging.sanitization.context.SanitizationContext;
import com.example.share.logging.sanitization.context.SanitizationRule;
import com.example.share.logging.sanitization.sanitizer.LogSanitizer;
import com.example.share.logging.sanitization.sanitizer.support.ValueSanitizer;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class JsonBodySanitizer implements LogSanitizer { // 去掉 implements BodyFilter

  private final Map<String, SanitizationRule> jsonPathRules;
  private final ValueSanitizer valueSanitizer;
  private final boolean enabled;

  private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
    .jsonProvider(new JacksonJsonProvider())
    .mappingProvider(new JacksonMappingProvider())
    .options(Option.SUPPRESS_EXCEPTIONS)
    .build();

  public JsonBodySanitizer(SanitizationContext config, ValueSanitizer valueSanitizer) {
    this.jsonPathRules = config.getJsonPathRules();
    this.valueSanitizer = valueSanitizer;
    this.enabled = config.getGlobalConfig().enable();

    log.info("Initialized JSON Body  Filter with {} rules.", jsonPathRules.size());
    log.debug("JSON Body  rules: {}", jsonPathRules.keySet());
  }

  @Override
  public void sanitizeRequest(HttpExchangeLog httpExchangeLog) {
    if (httpExchangeLog.getRequestContent() != null) {
      String maskedBody = handler(httpExchangeLog.getContentType(), httpExchangeLog.getRequestContent());
      httpExchangeLog.setRequestContent(maskedBody);
    }
  }

  @Override
  public void sanitizeResponse(HttpExchangeLog httpExchangeLog) {
    if (httpExchangeLog.getResponseContent() != null) {
      // 注意：响应的 ContentType 有时在 Logbook 中可能没正确传递，
      // 严谨起见应该优先取 log.getContentType() (Logbook 只有 request 有 contentType 字段)
      // 或者通过 response header 判断。这里假设 log 实体上有 contentType 字段复用
      String maskedBody = handler(httpExchangeLog.getContentType(), httpExchangeLog.getResponseContent());
      httpExchangeLog.setResponseContent(maskedBody);
    }
  }

  public String handler(String contentType, @Nonnull String body) {
    if (!enabled || !isJson(contentType) || jsonPathRules.isEmpty() || body.isBlank()) {
      return body;
    }

    long startTime = System.nanoTime();
    AtomicInteger matchCount = new AtomicInteger(0);

    try {
      DocumentContext context = JsonPath.using(JSON_PATH_CONFIG).parse(body);

      for (Map.Entry<String, SanitizationRule> entry : jsonPathRules.entrySet()) {
        String path = entry.getKey();
        SanitizationRule fieldConfig = entry.getValue();

        context.map(path, (currentValue, config) -> {
          if (currentValue == null) return null;

          // 记录命中详情 (Debug 级别)
          if (log.isDebugEnabled()) {
            log.debug("JsonPath matched: [{}], Strategy: [{}]", path, fieldConfig.strategy());
          }
          matchCount.incrementAndGet();

          return valueSanitizer.sanitize(currentValue.toString(), fieldConfig);
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
