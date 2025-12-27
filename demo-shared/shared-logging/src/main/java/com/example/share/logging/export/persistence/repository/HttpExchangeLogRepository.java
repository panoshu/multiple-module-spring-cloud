package com.example.share.logging.export.persistence.repository;

import com.example.share.logging.core.model.HttpExchangeLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class HttpExchangeLogRepository {

  private final JdbcClient jdbcClient;

  /**
   * 幂等写入 Request
   * 逻辑：如果 correlation_id 不存在则插入；如果已存在（Response 先到了），则只更新请求相关字段和 request_time
   */
  @Transactional
  public void upsertRequest(HttpExchangeLog log) {
    String sql = """
        INSERT INTO http_exchange_log (
            correlation_id, created_time,
            request_time, method, uri, remote,
            request_headers, request_content, content_type,
            complete, status_code, duration_millis, truncated
        ) VALUES (
            :correlationId, :createdTime,
            :requestTime, :method, :uri, :remote,
            :requestHeaders, :requestContent, :contentType,
            false, 0, 0, false
        )
        ON DUPLICATE KEY UPDATE
            request_time = VALUES(request_time),
            method = VALUES(method),
            uri = VALUES(uri),
            remote = VALUES(remote),
            request_headers = VALUES(request_headers),
            request_content = VALUES(request_content),
            content_type = VALUES(content_type)
        """;

    jdbcClient.sql(sql)
      .param("correlationId", log.getCorrelationId().toString()) // UUID 转 String
      .param("createdTime", log.getCreatedTime())
      .param("requestTime", log.getRequestTime())
      .param("method", log.getMethod())
      .param("uri", log.getUri())
      .param("remote", log.getRemote())
      .param("requestHeaders", log.getRequestHeaders())
      .param("requestContent", log.getRequestContent())
      .param("contentType", log.getContentType())
      .update();
  }

  /**
   * 幂等写入 Response
   * 逻辑：如果 correlation_id 不存在则插入（请求字段为空）；如果已存在，则更新响应相关字段和 response_time
   */
  @Transactional
  public void upsertResponse(HttpExchangeLog log) {
    String sql = """
        INSERT INTO http_exchange_log (
            correlation_id, created_time,
            response_time, status_code, duration_millis,
            response_headers, response_content,
            client_info, ip, user_agent,
            complete, truncated,
            method, uri, remote, request_time
        ) VALUES (
            :correlationId, :createdTime,
            :responseTime, :statusCode, :durationMillis,
            :responseHeaders, :responseContent,
            :clientInfo, :ip, :userAgent,
            true, :truncated,
            :method, :uri, :remote, :requestTime
        )
        ON DUPLICATE KEY UPDATE
            response_time = VALUES(response_time),
            status_code = VALUES(status_code),
            duration_millis = VALUES(duration_millis),
            response_headers = VALUES(response_headers),
            response_content = VALUES(response_content),
            client_info = VALUES(client_info),
            ip = VALUES(ip),
            user_agent = VALUES(user_agent),
            complete = true,
            truncated = VALUES(truncated)
        """;

    jdbcClient.sql(sql)
      .param("correlationId", log.getCorrelationId().toString())
      .param("createdTime", log.getCreatedTime())
      .param("responseTime", log.getResponseTime())
      .param("statusCode", log.getStatusCode())
      .param("durationMillis", log.getDurationMillis())
      .param("responseHeaders", log.getResponseHeaders())
      .param("responseContent", log.getResponseContent())
      .param("clientInfo", log.getClientInfo())
      .param("ip", log.getIp())
      .param("userAgent", log.getUserAgent())
      .param("truncated", log.isTruncated())
      .param("method", log.getMethod())
      .param("uri", log.getUri())
      .param("remote", log.getRemote())
      .param("requestTime", log.getRequestTime())
      .update();
  }
}
