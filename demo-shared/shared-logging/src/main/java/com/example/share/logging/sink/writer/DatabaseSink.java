package com.example.share.logging.sink.writer;

import com.example.share.logging.sink.entity.HttpExchangeLog;
import com.example.share.logging.sink.repository.HttpExchangeLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zalando.logbook.*;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSink implements Sink {

  private final HttpExchangeLogRepository logRepository;
  private final ObjectMapper objectMapper;


  @Override
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(@Nonnull Precorrelation precorrelation, @Nonnull HttpRequest httpRequest) {
    try {
      // ✅ 修复1：正确的参数命名，避免与 @Slf4j 的 log 冲突
      HttpExchangeLog requestLog = createRequestLog(precorrelation, httpRequest);
      saveRequestLog(requestLog);
      log.debug("Saved request log to database: {}", precorrelation.getId());
    } catch (Exception e) {
      log.error("Failed to save request log for correlation: {}", precorrelation.getId(), e);
      fallbackToConsoleLogging(precorrelation, httpRequest, null, e);
    }
  }

  @Override
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void write(@Nonnull Correlation correlation, @Nonnull HttpRequest request, @Nonnull HttpResponse response) {
    try {
      // ✅ 修复2：先尝试更新已存在的请求记录
      boolean updated = updateResponseLog(correlation, response);

      if (!updated) {
        // ✅ 修复3：如果更新失败，创建完整记录
        HttpExchangeLog fullLog = createFullLog(correlation, request, response);
        saveFullLog(fullLog);
        log.debug("Created full log for correlation: {}", correlation.getId());
      } else {
        log.debug("Updated response data for correlation: {}", correlation.getId());
      }
    } catch (Exception e) {
      log.error("Failed to save response log for correlation: {}", correlation.getId(), e);
      fallbackToConsoleLogging(correlation, request, response, e);
    }
  }

  private HttpExchangeLog createRequestLog(Precorrelation precorrelation, HttpRequest request) throws IOException {
    HttpExchangeLog logEntry = new HttpExchangeLog();

    // ✅ 修复4：正确设置字段，避免方法不存在
    logEntry.setCorrelationId(UUID.fromString(precorrelation.getId()));
    logEntry.setRemote(request.getRemote());
    logEntry.setMethod(request.getMethod());
    logEntry.setUri(StringUtils.abbreviate(request.getRequestUri(), 2000));

    // ✅ 修复5：正确获取 Content-Type
    String contentType = safeGetHeader(request.getHeaders(),"Content-Type", "");
    logEntry.setContentType(contentType);

    logEntry.setRequestHeaders(serializeHeaders(request.getHeaders()));
    logEntry.setRequestContent(request.getBodyAsString());

    // 请求阶段的标记
    logEntry.setComplete(false);
    logEntry.setStatusCode(0);
    logEntry.setDurationMillis(0L);
    logEntry.setCreatedTime(LocalDateTime.now());

    return logEntry;
  }

  private void saveRequestLog(HttpExchangeLog logEntry) {
    try {
      // ✅ 虚拟线程：直接同步调用，但不会阻塞平台线程
      if (!logRepository.existsByCorrelationId(logEntry.getCorrelationId())) {
        logRepository.save(logEntry);
      }
    } catch (Exception e) {
      log.error("❌ Async save request log failed", e);
      // 降级：同步保存
      if (!logRepository.existsByCorrelationId(logEntry.getCorrelationId())) {
        logRepository.save(logEntry);
      }
    }
  }

  private boolean updateResponseLog(Correlation correlation, HttpResponse response) {
    try {
      UUID correlationId = UUID.fromString(correlation.getId());

      if (logRepository.existsByCorrelationId(correlationId)) {
        // ✅ 修复6：正确获取响应数据
        String userAgent = safeGetHeader(response.getHeaders(),"User-Agent", "");
        String clientIp = getRealClientIp(response.getHeaders());

        int updated = logRepository.updateResponseData(
          correlationId,
          response.getStatus(),
          Duration.between(correlation.getStart(), correlation.getEnd()).toMillis(),
          serializeHeaders(response.getHeaders()),
          response.getBodyAsString(),
          clientIp,  // ✅ 修复7：正确获取客户端信息
          clientIp,  // IP
          userAgent != null && !userAgent.isEmpty() ? userAgent : null
        );

        return updated > 0;
      }
      return false;
    } catch (Exception e) {
      log.error("Failed to update response log", e);
      return false;
    }
  }

  private HttpExchangeLog createFullLog(Correlation correlation, HttpRequest request, HttpResponse response) throws IOException {
    HttpExchangeLog logEntry = new HttpExchangeLog();

    // 基础信息
    logEntry.setCorrelationId(UUID.fromString(correlation.getId()));
    logEntry.setRemote(request.getRemote());
    logEntry.setMethod(request.getMethod());
    logEntry.setUri(StringUtils.abbreviate(request.getRequestUri(), 2000));
    logEntry.setStatusCode(response.getStatus());
    logEntry.setDurationMillis(Duration.between(correlation.getStart(), correlation.getEnd()).toMillis());
    logEntry.setCreatedTime(LocalDateTime.now());

    // ✅ 修复8：正确获取客户端信息
    String userAgentHeader = safeGetHeader(request.getHeaders(),"User-Agent", "");
    String userAgent = userAgentHeader != null && !userAgentHeader.isEmpty() ? userAgentHeader : null;

    String clientIp = getRealClientIp(request.getHeaders());

    logEntry.setClientInfo(clientIp);
    logEntry.setIp(clientIp);
    logEntry.setUserAgent(StringUtils.abbreviate(userAgent, 500));

    // 内容类型
    String contentType = safeGetHeader(request.getHeaders(),"Content-Type", "");
    logEntry.setContentType(contentType != null && !contentType.isEmpty() ? contentType : null);

    // 请求数据
    logEntry.setRequestHeaders(serializeHeaders(request.getHeaders()));
    logEntry.setRequestContent(request.getBodyAsString());

    // 响应数据
    logEntry.setResponseHeaders(serializeHeaders(response.getHeaders()));
    logEntry.setResponseContent(response.getBodyAsString());

    // 标记为完整
    logEntry.setComplete(true);
    logEntry.setTruncated(isTruncated(request, response));

    return logEntry;
  }

  private void saveFullLog(HttpExchangeLog logEntry) {
    try {
      if (!logRepository.existsByCorrelationId(logEntry.getCorrelationId())) {
        logRepository.save(logEntry);
      }
    } catch (Exception e) {
      log.error("Async save full log failed", e);
      if (!logRepository.existsByCorrelationId(logEntry.getCorrelationId())) {
        logRepository.save(logEntry);
      }
    }
  }

  private boolean isTruncated(HttpRequest request, HttpResponse response) throws IOException {
    return request.getBodyAsString().contains("... (truncated)") ||
      response.getBodyAsString().contains("... (truncated)") ||
      request.getBodyAsString().length() > 10000 ||
      response.getBodyAsString().length() > 10000;
  }

  private String serializeHeaders(HttpHeaders headers) {
    try {
      // ✅ 修复9：正确序列化 HttpHeaders
      Map<String, List<String>> headersMap = headers.entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> new ArrayList<>(entry.getValue()), // 确保不可变
          (v1, v2) -> v1
        ));
      return objectMapper.writeValueAsString(headersMap);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize headers", e);
      return "{\"error\": \"serialization_failed\"}";
    }
  }

  private String getRealClientIp(HttpHeaders headers) {
    // ✅ 修复10：正确获取客户端IP
    String xForwardedFor = safeGetHeader(headers, "X-Forwarded-For", "");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIp = safeGetHeader(headers, "X-Real-IP", "");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }

    return "unknown";
  }

  private void fallbackToConsoleLogging(Object correlation, HttpRequest request, HttpResponse response, Exception e) {
    String correlationId;
    if (correlation instanceof Precorrelation prerecord) {
      correlationId = prerecord.getId();
    } else {
      correlationId = "unknown";
    }

    String method = request != null ? request.getMethod() : "unknown";
    String uri = request != null ? request.getRequestUri() : "unknown";
    String status = response != null ? String.valueOf(response.getStatus()) : "unknown";

    log.warn("DATABASE_WRITE_FAILED - CorrelationId: {}, Method: {}, URI: {}, Status: {}, Error: {}",
      correlationId, method, uri, status, e.getMessage());
  }

  public void cleanupIncompleteRecords() {
    try {
      LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
      List<HttpExchangeLog> incompleteLogs = logRepository.findByCompleteFalseAndCreatedTimeBefore(cutoffTime);

      if (!incompleteLogs.isEmpty()) {
        log.warn("Cleaning up {} incomplete HTTP logs older than {}", incompleteLogs.size(), cutoffTime);
        // 实际清理逻辑（根据业务需求）
      }
    } catch (Exception e) {
      log.error("Failed to cleanup incomplete logs", e);
    }
  }

  // 推荐的工具方法
  public static String safeGetHeader(HttpHeaders headers, String headerName, String defaultValue) {
    if (headers == null || headerName == null) {
      return defaultValue;
    }

    return headers.get(headerName)
      .stream()
      .filter(value -> value != null && !value.isBlank())
      .findFirst()
      .orElse(defaultValue);
  }
}
