package com.example.share.logging.export.persistence.repository;

import com.example.share.logging.core.model.HttpExchangeLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractHttpExchangeLogRepository implements HttpExchangeLogRepository {

  private static final int MAX_RAW_CONTENT_LENGTH = 2000;
  protected final JdbcClient jdbcClient;
  protected final ObjectMapper objectMapper;

  /**
   * 模板方法：定义 Upsert 的标准流程
   */
  @Override
  @Transactional
  @SuppressWarnings("SqlSourceToSinkFlow")
  public void upsert(HttpExchangeLog httpExchangeLog) {
    if (httpExchangeLog == null) {
      log.warn("Attempt to upsert null HttpExchangeLog. Ignored.");
      return;
    }

    try {
      // 1. 领域自检
      httpExchangeLog.validate();

      if (log.isDebugEnabled()) {
        log.debug("Upserting log entry. correlationId={}, method={}, complete={}",
          httpExchangeLog.getCorrelationId(), httpExchangeLog.getMethod(), httpExchangeLog.isComplete());
      }

      // 2. 准备参数 (差异化逻辑已统一封装在 convertParams 中)
      Map<String, Object> params = convertParams(httpExchangeLog);

      // 3. 获取 SQL (由子类提供)
      String sql = getUpsertSql();

      // 4. 执行
      jdbcClient.sql(sql)
        .params(params)
        .update();

      if (log.isDebugEnabled()) {
        log.debug("Successfully upserted log. correlationId={}", httpExchangeLog.getCorrelationId());
      }

    } catch (IllegalArgumentException e) {
      log.error("Validation failed for log: {}", httpExchangeLog, e);
      throw e;
    } catch (Exception e) {
      log.error("Failed to upsert HTTP exchange log. correlationId={}, service={}, uri={}",
        httpExchangeLog.getCorrelationId(), httpExchangeLog.getServiceName(), httpExchangeLog.getUri(), e);
    }
  }

  /**
   * 扩展点：子类提供特定的 Upsert SQL
   */
  protected abstract String getUpsertSql();

  /**
   * 统一参数转换逻辑
   */
  private Map<String, Object> convertParams(HttpExchangeLog httpExchangeLog) {
    Map<String, Object> map = new HashMap<>();
    String cid = httpExchangeLog.getCorrelationId();

    map.put("correlationId", cid);
    map.put("serviceName", httpExchangeLog.getServiceName());
    map.put("createdTime", httpExchangeLog.getCreatedTime());
    map.put("requestTime", httpExchangeLog.getRequestTime());
    map.put("method", httpExchangeLog.getMethod());
    map.put("uri", httpExchangeLog.getUri());
    map.put("remote", httpExchangeLog.getRemote());

    // JSON 处理
    map.put("requestHeaders", ensureJsonOrWrap(httpExchangeLog.getRequestHeaders(), "requestHeaders", cid));
    map.put("requestContent", ensureJsonOrWrap(httpExchangeLog.getRequestContent(), "requestContent", cid));
    map.put("responseHeaders", ensureJsonOrWrap(httpExchangeLog.getResponseHeaders(), "responseHeaders", cid));
    map.put("responseContent", ensureJsonOrWrap(httpExchangeLog.getResponseContent(), "responseContent", cid));

    map.put("contentType", httpExchangeLog.getContentType());
    map.put("responseTime", httpExchangeLog.getResponseTime());
    map.put("statusCode", httpExchangeLog.getStatusCode());
    map.put("durationMillis", httpExchangeLog.getDurationMillis());
    map.put("clientInfo", httpExchangeLog.getClientInfo());
    map.put("ip", httpExchangeLog.getIp());
    map.put("userAgent", httpExchangeLog.getUserAgent());
    map.put("complete", httpExchangeLog.isComplete());
    map.put("truncated", httpExchangeLog.isTruncated());

    return map;
  }

  /**
   * 安全转换字符串为合法 JSONB 内容，非 JSON 则包装。
   */
  private String ensureJsonOrWrap(String value, String fieldName, String correlationId) {
    if (value == null || value.isBlank()) {
      return null;
    }

    // 【优化】简单预判，减少异常抛出
    // 如果不以 { 或 [ 开头，肯定不是标准 JSON 对象/数组，直接跳过 parse 尝试
    String trimmed = value.trim();
    boolean looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[");

    if (looksLikeJson) {
      try {
        // 尝试解析为合法 JSON
        objectMapper.readTree(value);
        return value;
      } catch (JsonProcessingException ignored) {
        // 虽然像 JSON 但解析失败，进入包装逻辑
      }
    }

    // 非法 JSON 或普通文本，进入包装逻辑
    return wrapTextAsJson(value, fieldName, correlationId);
  }

  private String wrapTextAsJson(String value, String fieldName, String correlationId) {
    int originalLength = value.length();
    boolean willTruncate = originalLength > MAX_RAW_CONTENT_LENGTH;
    String truncatedValue = willTruncate ? value.substring(0, MAX_RAW_CONTENT_LENGTH) : value;

    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put("raw", willTruncate ? "%s...".formatted(truncatedValue) : truncatedValue);
    wrapper.put("length", originalLength);
    // wrapper.put("type", "non-json-text"); // 可选：标记一下

    try {
      String wrappedJson = objectMapper.writeValueAsString(wrapper);
      // debug 级别即可，没必要 warn，因为这不是错误，只是格式转换
      log.debug("Wrapped content. id={}, field={}", correlationId, fieldName);
      return wrappedJson;
    } catch (JsonProcessingException e) {
      log.error("Failed to wrap content. id={}", correlationId, e);
      return "{}"; // 兜底空对象
    }
  }
}
