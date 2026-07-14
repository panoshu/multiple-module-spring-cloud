package com.example.share.logging.core.pipeline;

import com.example.share.logging.core.api.LogProcessor;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.export.dispatcher.LogExporterDispatcher;
import com.example.share.logging.sanitization.engine.SanitizationEngine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@RequiredArgsConstructor
public class AsyncLogPipeline implements LogProcessor {

  // 必须与 Aspect 中定义的 Key 保持一致
  private static final String CONTEXT_BACKUP_KEY = "Trace_Context_Backup_Map";
  private final SanitizationEngine sanitizationEngine;
  private final LogExporterDispatcher exporterDispatcher;
  private final Executor executor;

  @Override
  public void processRequest(HttpExchangeLog httpExchangeLog) {
    // 1. 获取完整的上下文 (MDC + Request Backup)
    Map<String, String> combinedContext = captureCombinedContext();

    executor.execute(() -> {
      // 2. 恢复上下文
      MDC.setContextMap(combinedContext);
      try {
        sanitizationEngine.sanitizeRequest(httpExchangeLog);
        exporterDispatcher.exportRequest(httpExchangeLog);
      } catch (Exception e) {
        log.error("AsyncLogPipeline processRequest error", e);
      } finally {
        MDC.clear();
      }
    });
  }

  @Override
  public void processResponse(HttpExchangeLog httpExchangeLog) {
    // 1. 获取完整的上下文 (MDC + Request Backup)
    Map<String, String> combinedContext = captureCombinedContext();

    executor.execute(() -> {
      // 2. 恢复上下文
      MDC.setContextMap(combinedContext);
      try {
        sanitizationEngine.sanitizeResponse(httpExchangeLog);
        sanitizationEngine.sanitizeRequest(httpExchangeLog);
        exporterDispatcher.exportResponse(httpExchangeLog);
      } catch (Exception e) {
        log.error("AsyncLogPipeline processResponse error", e);
      } finally {
        MDC.clear();
      }
    });
  }

  /**
   * 【核心修复】：捕获上下文，合并 MDC 和 Request Attribute 中的备份数据
   */
  private Map<String, String> captureCombinedContext() {
    // 1. 先拿当前的 MDC (可能缺少部分 Body 解析的 ID)
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    if (contextMap == null) {
      contextMap = new HashMap<>();
    }

    // 2. 尝试从 Request Attribute 中获取备份 (这里面有 Aspect 解析 Body 得到的 ID)
    try {
      var attrs = RequestContextHolder.getRequestAttributes();
      if (attrs instanceof ServletRequestAttributes sra) {
        HttpServletRequest request = sra.getRequest();
        Object backup = request.getAttribute(CONTEXT_BACKUP_KEY);
        if (backup instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, String> backupMap = (Map<String, String>) backup;
          // 合并备份数据 (备份数据优先级更高，或者作为补充)
          contextMap.putAll(backupMap);
        }
      }
    } catch (Exception e) {
      // 忽略获取 Request 失败的情况 (比如非 Web 环境)
    }

    return contextMap;
  }
}
