package com.example.shared.web.trace.integration;

import com.example.shared.web.trace.config.TraceContextProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

/**
 * Feign 业务上下文透传拦截器
 */
@RequiredArgsConstructor
public class FeignTraceInterceptor implements RequestInterceptor {

  private final TraceContextProperties properties;

  @Override
  public void apply(RequestTemplate template) {
    // 遍历配置中定义的需要透传的 Header
    for (String alias : properties.getAliases()) {
      String value = MDC.get(alias);
      String headerKey = properties.getHeaderKey(alias);
      // 如果 MDC 中有值，且 Header 中还没设置，则注入
      if (value != null && !template.headers().containsKey(headerKey)) {
        template.header(headerKey, value);
      }
    }
  }
}
