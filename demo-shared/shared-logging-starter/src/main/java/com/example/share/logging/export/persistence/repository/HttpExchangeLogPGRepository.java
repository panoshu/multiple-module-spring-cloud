package com.example.share.logging.export.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class HttpExchangeLogPGRepository extends AbstractHttpExchangeLogRepository {

  public HttpExchangeLogPGRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    super(jdbcClient, objectMapper);
  }

  @Override
  protected String getUpsertSql() {
    // PG 特有语法: :param::jsonb, ON CONFLICT
    return """
      INSERT INTO http_exchange_log (
          correlation_id, service_name, created_time,
          request_time, method, uri, remote,
          request_headers, request_content, content_type,
          response_time, status_code, duration_millis,
          response_headers, response_content,
          client_info, ip, user_agent,
          complete, truncated
      ) VALUES (
          :correlationId, :serviceName, :createdTime,
          :requestTime, :method, :uri, :remote,
          :requestHeaders::jsonb, :requestContent::jsonb, :contentType,
          :responseTime, :statusCode, :durationMillis,
          :responseHeaders::jsonb, :responseContent::jsonb,
          :clientInfo, :ip, :userAgent,
          :complete, :truncated
      )
      ON CONFLICT (correlation_id) DO UPDATE SET
          service_name = EXCLUDED.service_name,
          created_time = EXCLUDED.created_time,
          request_time = COALESCE(EXCLUDED.request_time, http_exchange_log.request_time),
          method = COALESCE(EXCLUDED.method, http_exchange_log.method),
          uri = COALESCE(EXCLUDED.uri, http_exchange_log.uri),
          remote = COALESCE(EXCLUDED.remote, http_exchange_log.remote),
          request_headers = COALESCE(EXCLUDED.request_headers, http_exchange_log.request_headers),
          request_content = COALESCE(EXCLUDED.request_content, http_exchange_log.request_content),
          content_type = COALESCE(EXCLUDED.content_type, http_exchange_log.content_type),
          response_time = COALESCE(EXCLUDED.response_time, http_exchange_log.response_time),
          status_code = COALESCE(EXCLUDED.status_code, http_exchange_log.status_code),
          duration_millis = COALESCE(EXCLUDED.duration_millis, http_exchange_log.duration_millis),
          response_headers = COALESCE(EXCLUDED.response_headers, http_exchange_log.response_headers),
          response_content = COALESCE(EXCLUDED.response_content, http_exchange_log.response_content),
          client_info = COALESCE(EXCLUDED.client_info, http_exchange_log.client_info),
          ip = COALESCE(EXCLUDED.ip, http_exchange_log.ip),
          user_agent = COALESCE(EXCLUDED.user_agent, http_exchange_log.user_agent),
          complete = (http_exchange_log.complete OR EXCLUDED.complete),
          truncated = (http_exchange_log.truncated OR EXCLUDED.truncated)
      """;
  }
}
