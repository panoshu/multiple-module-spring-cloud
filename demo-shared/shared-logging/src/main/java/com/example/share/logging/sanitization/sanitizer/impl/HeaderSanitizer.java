package com.example.share.logging.sanitization.sanitizer.impl;

import com.example.share.logging.sanitization.context.SanitizationRule;
import com.example.share.logging.sanitization.context.SanitizationContext;
import com.example.share.logging.sanitization.sanitizer.LogSanitizer;
import com.example.share.logging.sanitization.sanitizer.support.ValueSanitizer;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Header 脱敏服务
 * 不再依赖 Logbook 接口，作为独立组件运行
 */
@Slf4j
public class HeaderSanitizer implements LogSanitizer { // 去掉 implements HeaderFilter

  private final Map<String, SanitizationRule> headerRules;
  private final ValueSanitizer valueSanitizer;
  private final ObjectMapper objectMapper;
  private final boolean enabled;

  public HeaderSanitizer(SanitizationContext config, ValueSanitizer valueSanitizer, ObjectMapper objectMapper) {
    this.headerRules = config.getHeaderRules();
    this.valueSanitizer = valueSanitizer;
    this.objectMapper = objectMapper;
    this.enabled = config.getGlobalConfig().enable();

    log.info("Initialized Header Filter with {} rules.", headerRules.size());
    log.debug("Header rules: {}", headerRules.keySet());

  }

  @Override
  public void sanitizeRequest(HttpExchangeLog httpExchangeLog) {
    httpExchangeLog.setRequestHeaders(processHeaders(httpExchangeLog.getRequestHeaders()));
  }

  @Override
  public void sanitizeResponse(HttpExchangeLog httpExchangeLog) {
    httpExchangeLog.setResponseHeaders(processHeaders(httpExchangeLog.getResponseHeaders()));
  }

  private String processHeaders(String jsonHeaders) {
    if (jsonHeaders == null || jsonHeaders.equals("{}")) return jsonHeaders;
    try {
      Map<String, List<String>> headers = objectMapper.readValue(jsonHeaders, new TypeReference<>() {});
      Map<String, List<String>> maskedHeaders = handler(headers);
      return objectMapper.writeValueAsString(maskedHeaders);
    } catch (Exception e) {
      log.warn("Failed to process headers", e);
      return jsonHeaders;
    }
  }


  // 专门处理 Map<String, List<String>> 结构的方法
  public Map<String, List<String>> handler(Map<String, List<String>> headers) {
    if (!enabled || headerRules.isEmpty() || headers == null || headers.isEmpty()) {
      return headers;
    }

    try {
      // 遍历并替换值
      return headers.entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> {
            SanitizationRule rule = headerRules.get(entry.getKey().toLowerCase());
            if (rule == null) {
              return entry.getValue();
            }
            // 命中规则，执行脱敏
            return entry.getValue().stream()
              .map(v -> valueSanitizer.sanitize(v, rule))
              .toList();
          }
        ));
    } catch (Exception e) {
      log.error("Header obfuscation failed", e);
      return headers;
    }
  }
}
