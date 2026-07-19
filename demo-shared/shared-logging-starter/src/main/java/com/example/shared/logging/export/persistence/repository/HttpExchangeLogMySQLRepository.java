package com.example.shared.logging.export.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class HttpExchangeLogMySQLRepository extends AbstractHttpExchangeLogRepository {

  public HttpExchangeLogMySQLRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    super(jdbcClient, objectMapper);
  }

  @Override
  protected String getUpsertSql() {
    // MySQL 特有语法: 无需 ::jsonb, ON DUPLICATE KEY UPDATE
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
          :requestHeaders, :requestContent, :contentType,
          :responseTime, :statusCode, :durationMillis,
          :responseHeaders, :responseContent,
          :clientInfo, :ip, :userAgent,
          :complete, :truncated
      )
      ON DUPLICATE KEY UPDATE
          service_name = VALUES(service_name),
          created_time = VALUES(created_time),
          request_time = COALESCE(VALUES(request_time), request_time),
          method = COALESCE(VALUES(method), method),
          uri = COALESCE(VALUES(uri), uri),
          remote = COALESCE(VALUES(remote), remote),
          request_headers = COALESCE(VALUES(request_headers), request_headers),
          request_content = COALESCE(VALUES(request_content), request_content),
          content_type = COALESCE(VALUES(content_type), content_type),
          response_time = COALESCE(VALUES(response_time), response_time),
          status_code = COALESCE(VALUES(status_code), status_code),
          duration_millis = COALESCE(VALUES(duration_millis), duration_millis),
          response_headers = COALESCE(VALUES(response_headers), response_headers),
          response_content = COALESCE(VALUES(response_content), response_content),
          client_info = COALESCE(VALUES(client_info), client_info),
          ip = COALESCE(VALUES(ip), ip),
          user_agent = COALESCE(VALUES(user_agent), user_agent),
          complete = (complete OR VALUES(complete)),
          truncated = (truncated OR VALUES(truncated))
      """;
  }
}
