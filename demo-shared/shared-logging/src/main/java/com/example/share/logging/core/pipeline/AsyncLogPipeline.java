package com.example.share.logging.core.pipeline;

import com.example.share.logging.core.api.LogProcessor;
import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.export.dispatcher.LogExporterDispatcher;
import com.example.share.logging.sanitization.engine.SanitizationEngine;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.Executor;

/**
 * AsyncLogPipeline
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/27 14:16
 */
@RequiredArgsConstructor
public class AsyncLogPipeline implements LogProcessor {

  private final SanitizationEngine sanitizationEngine; // 脱敏引擎
  private final LogExporterDispatcher exporterDispatcher; // 导出分发器
  private final Executor executor;

  @Override
  public void processRequest(HttpExchangeLog log) {
    executor.execute(() -> {
      try {
        // 1. 净化请求数据
        sanitizationEngine.sanitizeRequest(log);
        // 2. 导出/记录请求
        exporterDispatcher.exportRequest(log);
      } catch (Exception e) {
        // log error
      }
    });
  }

  @Override
  public void processResponse(HttpExchangeLog log) {
    executor.execute(() -> {
      try {
        // 1. 净化响应数据 (以及携带的请求URI等)
        sanitizationEngine.sanitizeResponse(log);
        // 2. 导出/记录响应
        exporterDispatcher.exportResponse(log);
      } catch (Exception e) {
        // log error
      }
    });
  }
}
