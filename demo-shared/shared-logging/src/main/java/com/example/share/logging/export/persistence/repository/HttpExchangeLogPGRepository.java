package com.example.share.logging.export.persistence.repository;

import com.example.share.logging.core.model.HttpExchangeLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class HttpExchangeLogPGRepository {

  private final JdbcClient jdbcClient;

  /**
   * 幂等写入 Request (PostgreSQL 版)
   */
  @Transactional
  public void upsertRequest(HttpExchangeLog log) {
    // 注意：request_headers::jsonb 和 request_content::jsonb
    // 这要求传入的字符串必须是合法的 JSON 格式，否则数据库会报错
    String sql = """
        INSERT INTO engine.http_exchange_log (
            correlation_id, created_time,
            request_time, method, uri, remote,
            request_headers, request_content, content_type,
            complete, status_code, duration_millis, truncated
        ) VALUES (
            :correlationId, :createdTime,
            :requestTime, :method, :uri, :remote,
            :requestHeaders::jsonb, :requestContent::jsonb, :contentType,
            false, 0, 0, false
        )
        ON CONFLICT (correlation_id) DO UPDATE SET
            request_time = EXCLUDED.request_time,
            method = EXCLUDED.method,
            uri = EXCLUDED.uri,
            remote = EXCLUDED.remote,
            request_headers = EXCLUDED.request_headers,
            request_content = EXCLUDED.request_content,
            content_type = EXCLUDED.content_type
        """;

    jdbcClient.sql(sql)
      .param("correlationId", log.getCorrelationId()) // 假设实体已改为 String
      .param("createdTime", log.getCreatedTime())
      .param("requestTime", log.getRequestTime())
      .param("method", log.getMethod())
      .param("uri", log.getUri())
      .param("remote", log.getRemote())
      // 如果内容为空或非JSON，建议在上层Mapper处理成 "{}" 或 null，防止SQL报错
      .param("requestHeaders", ensureJson(log.getRequestHeaders()))
      .param("requestContent", ensureJson(log.getRequestContent()))
      .param("contentType", log.getContentType())
      .update();
  }

  /**
   * 幂等写入 Response (PostgreSQL 版)
   */
  @Transactional
  public void upsertResponse(HttpExchangeLog log) {
    String sql = """
        INSERT INTO engine.http_exchange_log (
            correlation_id, created_time,
            response_time, status_code, duration_millis,
            response_headers, response_content,
            client_info, ip, user_agent,
            complete, truncated,
            method, uri, remote, request_time
        ) VALUES (
            :correlationId, :createdTime,
            :responseTime, :statusCode, :durationMillis,
            :responseHeaders::jsonb, :responseContent::jsonb,
            :clientInfo, :ip, :userAgent,
            true, :truncated,
            :method, :uri, :remote, :requestTime
        )
        ON CONFLICT (correlation_id) DO UPDATE SET
            response_time = EXCLUDED.response_time,
            status_code = EXCLUDED.status_code,
            duration_millis = EXCLUDED.duration_millis,
            response_headers = EXCLUDED.response_headers,
            response_content = EXCLUDED.response_content,
            client_info = EXCLUDED.client_info,
            ip = EXCLUDED.ip,
            user_agent = EXCLUDED.user_agent,
            complete = true,
            truncated = EXCLUDED.truncated
        """;

    jdbcClient.sql(sql)
      .param("correlationId", log.getCorrelationId())
      .param("createdTime", log.getCreatedTime())
      .param("responseTime", log.getResponseTime())
      .param("statusCode", log.getStatusCode())
      .param("durationMillis", log.getDurationMillis())
      .param("responseHeaders", ensureJson(log.getResponseHeaders()))
      .param("responseContent", ensureJson(log.getResponseContent()))
      .param("clientInfo", log.getClientInfo())
      .param("ip", log.getIp())
      .param("userAgent", log.getUserAgent())
      .param("truncated", log.isTruncated())
      // 下面这些参数主要用于 Insert 场景，Update 时不会用到
      .param("method", log.getMethod())
      .param("uri", log.getUri())
      .param("remote", log.getRemote())
      .param("requestTime", log.getRequestTime())
      .update();
  }

  /**
   * 辅助方法：确保入库字符串符合 JSONB 格式要求
   * Postgres JSONB 字段不能存普通文本，必须是 JSON 对象/数组 或 null
   */
  private String ensureJson(String value) {
    if (value == null || value.isBlank()) {
      return null; // 或者返回 "{}"
    }
    // 简单的校验：如果不是 { 开头也不是 [ 开头，说明可能是普通文本
    // 这种情况下强行存入 JSONB 会报错，建议包装一下
    String trimmed = value.trim();
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
      // 策略A: 包装成 JSON 字符串
      // return "\"" + value.replace("\"", "\\\"") + "\"";

      // 策略B: 包装成对象 (推荐)
      // return "{\"raw\": \"" + value.replace("\"", "\\\"") + "\"}";

      // 策略C: 如果你能保证 logbook 这里的 content 一定是 json，就直接返回
      // 否则这里如果不处理，遇到 "Hello World" 这种 body，SQL 会抛异常
      return null;
    }
    return value;
  }
}
