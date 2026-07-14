package com.example.share.logging.sanitization.sanitizer;

import com.example.share.logging.core.model.HttpExchangeLog;
import org.springframework.core.Ordered;

public interface LogSanitizer extends Ordered {

  // 处理请求日志
  void sanitizeRequest(HttpExchangeLog log);

  // 处理响应日志
  void sanitizeResponse(HttpExchangeLog log);

  // 默认优先级
  @Override
  default int getOrder() {
    return 0;
  }
}
