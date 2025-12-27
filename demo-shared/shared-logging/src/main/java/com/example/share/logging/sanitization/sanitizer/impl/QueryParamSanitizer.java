package com.example.share.logging.sanitization.sanitizer.impl;

import com.example.share.logging.sanitization.context.SanitizationRule;
import com.example.share.logging.sanitization.context.SanitizationContext;
import com.example.share.logging.sanitization.sanitizer.LogSanitizer;
import com.example.share.logging.sanitization.sanitizer.support.ValueSanitizer;
import com.example.share.logging.core.model.HttpExchangeLog;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class QueryParamSanitizer implements LogSanitizer { // 去掉 implements QueryFilter

  private final Map<String, SanitizationRule> queryRules;
  private final ValueSanitizer valueSanitizer;
  private final boolean enabled;
  private static final Pattern AMPERSAND = Pattern.compile("&");

  public QueryParamSanitizer(SanitizationContext config, ValueSanitizer valueSanitizer) {
    this.queryRules = config.getQueryRules();
    this.valueSanitizer = valueSanitizer;
    this.enabled = config.getGlobalConfig().enable();

    log.info("Initialized Query Param Filter with {} rules.", queryRules.size());
    log.debug("Query Param rules: {}", queryRules.keySet());
  }

  @Override
  public void sanitizeRequest(HttpExchangeLog httpExchangeLog) {
    handleUriObfuscation(httpExchangeLog);
  }

  @Override
  public void sanitizeResponse(HttpExchangeLog httpExchangeLog) {
    handleUriObfuscation(httpExchangeLog);
  }

  private void handleUriObfuscation(HttpExchangeLog httpExchangeLog) {
    if (!enabled || httpExchangeLog.getUri() == null || queryRules.isEmpty()) {
      return;
    }

    String originalUri = httpExchangeLog.getUri();
    // 简单判断是否包含 '?'，避免不必要的 split 开销
    int questionMarkIdx = originalUri.indexOf('?');
    if (questionMarkIdx == -1) {
      return;
    }

    String path = originalUri.substring(0, questionMarkIdx);
    String queryString = originalUri.substring(questionMarkIdx + 1);

    if (queryString.isEmpty()) {
      return;
    }

    // 调用核心脱敏逻辑
    String maskedQuery = handler(queryString);

    // 只有当内容发生变化时才通过 set 方法回写，减少对象创建
    if (!maskedQuery.equals(queryString)) {
      httpExchangeLog.setUri(path + "?" + maskedQuery);
    }
  }

  public String handler(String query) {
    if (!enabled || query == null || query.isEmpty() || queryRules.isEmpty()) {
      return query;
    }

    try {
      StringBuilder result = new StringBuilder(query.length() + 32);
      String[] pairs = AMPERSAND.split(query, -1);
      int matchCount = 0;

      for (int i = 0; i < pairs.length; i++) {
        String pair = pairs[i];
        int eqIdx = pair.indexOf('=');

        if (eqIdx > 0) {
          String key = decode(pair.substring(0, eqIdx));
          String value = pair.substring(eqIdx + 1);

          SanitizationRule rule = queryRules.get(key.toLowerCase());
          if (rule != null) {
            matchCount++;
            if (log.isTraceEnabled()) {
              log.trace("Obfuscating Query Param: [{}]", key);
            }

            String decodedVal = decode(value);
            String maskedVal = valueSanitizer.sanitize(decodedVal, rule);
            result.append(encode(key)).append('=').append(encode(maskedVal));
          } else {
            result.append(pair);
          }
        } else {
          result.append(pair);
        }

        if (i < pairs.length - 1) {
          result.append('&');
        }
      }

      if (matchCount > 0 && log.isDebugEnabled()) {
        log.debug("Sanitized {} query parameters", matchCount);
      }

      return result.toString();

    } catch (Exception e) {
      log.warn("Query param obfuscation failed. Returning original query. Error: {}", e.getMessage());
      return query;
    }
  }

  private String decode(String s) {
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("URL Decode failed for string snippet: {}", s); // Debug 级别，防止刷屏
      return s;
    }
  }

  private String encode(String s) {
    try {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("URL Encode failed for value", e);
      return s;
    }
  }
}
