package com.example.share.logging.sanitization.engine;

import com.example.share.logging.core.model.HttpExchangeLog;
import com.example.share.logging.sanitization.sanitizer.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanitizationEngine {

  private final List<LogSanitizer> logSanitizers;

  public void sanitizeRequest(HttpExchangeLog logEntity) {
    logThreadInfo("Request Obfuscation");
    for (LogSanitizer logSanitizer : logSanitizers) {
      try {
        logSanitizer.sanitizeRequest(logEntity);
      } catch (Exception e) {
        // 单个组件失败不应该影响其他组件，也不应阻断日志流程
        log.error("Sanitize {} failed on Request", logSanitizer.getClass().getSimpleName(), e);
      }
    }
  }

  public void sanitizeResponse(HttpExchangeLog logEntity) {
    logThreadInfo("Response Obfuscation");
    for (LogSanitizer logSanitizer : logSanitizers) {
      try {
        logSanitizer.sanitizeResponse(logEntity);
      } catch (Exception e) {
        log.error("Sanitize {} failed on Response", logSanitizer.getClass().getSimpleName(), e);
      }
    }
  }

  // 辅助方法：打印当前是否在虚拟线程中
  private void logThreadInfo(String stage) {
    if (log.isDebugEnabled()) {
      Thread current = Thread.currentThread();
      log.debug(">>> [{}] Executing in Thread: {} | IsVirtual: {}",
        stage, current, isVirtual(current));
    }
  }

  private boolean isVirtual(Thread thread) {
    try {
      return thread.isVirtual();
    } catch (NoSuchMethodError e) {
      return false; // JDK < 21
    }
  }
}
