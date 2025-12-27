package com.example.shared.core.trace.concurrency;

import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import java.util.Map;

/**
 * 线程池装饰器：实现 MDC 上下文在主线程与子线程间的自动透传
 */
public class MdcTaskDecorator implements TaskDecorator {

  @NonNull
  @Override
  public Runnable decorate(@NonNull Runnable runnable) {
    // 1. 在主线程：抓取当前 MDC 上下文
    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    return () -> {
      // 2. 在子线程：恢复上下文
      if (contextMap != null) {
        MDC.setContextMap(contextMap);
      }
      try {
        runnable.run();
      } finally {
        // 3. 在子线程：清理上下文，防止线程复用污染
        MDC.clear();
      }
    };
  }
}
