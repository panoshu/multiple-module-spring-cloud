package com.example.share.logging.writer.mapper;

import com.example.share.logging.writer.entity.HttpExchangeLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 核心职责：将 Logbook 的领域对象转换为持久化实体 HttpExchangeLog
 * 设计模式：Converter / Mapper / Factory
 */
@Slf4j
@RequiredArgsConstructor
public class HttpExchangeLogMapper {

  private final ObjectMapper objectMapper;

  /**
   * 工厂方法：构建请求阶段的日志实体
   */
  public HttpExchangeLog toRequestLog(Precorrelation precorrelation, HttpRequest request) {
    HttpExchangeLog logEntity = new HttpExchangeLog();

    // ID 与 时间
    logEntity.setCorrelationId(precorrelation.getId());
    LocalDateTime now = LocalDateTime.now();
    logEntity.setCreatedTime(now);
    logEntity.setRequestTime(now);

    // 填充请求数据
    fillRequestData(logEntity, request);

    // 初始状态
    logEntity.setTruncated(false);

    return logEntity;
  }

  /**
   * 工厂方法：构建响应阶段的日志实体
   */
  public HttpExchangeLog toResponseLog(Correlation correlation, HttpRequest request, HttpResponse response) {
    HttpExchangeLog logEntity = new HttpExchangeLog();

    // ID
    logEntity.setCorrelationId(correlation.getId());
    // 时间计算 (从 Correlation 获取精准的开始时间)
    LocalDateTime startTime = LocalDateTime.ofInstant(correlation.getStart(), ZoneId.systemDefault());
    logEntity.setCreatedTime(startTime);
    logEntity.setRequestTime(startTime);
    logEntity.setResponseTime(LocalDateTime.now());
    logEntity.setDurationMillis(correlation.getDuration().toMillis());

    // 填充响应数据
    logEntity.setStatusCode(response.getStatus());
    logEntity.setResponseHeaders(serializeHeaders(response.getHeaders()));
    logEntity.setResponseContent(safeGetBody(response));

    // 【关键】即使是响应阶段，也重新填充 Request 元数据
    // 保证 Response 先入库时，数据也是完整的
    fillRequestData(logEntity, request);

    logEntity.setComplete(true);

    return logEntity;
  }

  private void fillRequestData(HttpExchangeLog logEntity, HttpRequest request) {
    logEntity.setMethod(request.getMethod());
    logEntity.setUri(request.getRequestUri());
    logEntity.setRemote(request.getRemote());
    logEntity.setContentType(request.getContentType());
    logEntity.setRequestHeaders(serializeHeaders(request.getHeaders()));
    logEntity.setRequestContent(safeGetBody(request));

    // 客户端指纹
    logEntity.setUserAgent(safeGetHeader(request, "User-Agent"));
    logEntity.setIp(extractClientIp(request));
  }

  private String serializeHeaders(Object headers) {
    if (headers == null) {
      return "{}";
    }
    // 简单的 Map 判空优化，避免 Jackson 调用
    if (headers instanceof Map && ((Map<?, ?>) headers).isEmpty()) {
      return "{}";
    }

    try {
      return objectMapper.writeValueAsString(headers);
    } catch (Exception e) {
      log.warn("Failed to serialize headers. Error: {}", e.getMessage());
      return "{}";
    }
  }

  private String safeGetBody(HttpMessage message) {
    try {
      String body = message.getBodyAsString();
      return body != null ? body : "";
    } catch (IOException e) {
      log.warn("Failed to read body from message. Error: {}", e.getMessage());
      return "";
    }
  }

  private String safeGetHeader(HttpRequest request, String headerName) {
    var headers = request.getHeaders();
    if (headers == null) return "";

    return headers.entrySet().stream()
      .filter(e -> e.getKey().equalsIgnoreCase(headerName))
      .findFirst()
      .map(e -> String.join(",", e.getValue()))
      .orElse("");
  }

  private String extractClientIp(HttpRequest request) {
    String xForwardedFor = safeGetHeader(request, "X-Forwarded-For");
    return xForwardedFor.isEmpty() ? request.getRemote() : xForwardedFor.split(",")[0].trim();
  }
}
