package com.example.shared.web.trace.integration;

import com.example.shared.web.trace.config.TraceContextProperties;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Spring Web Client (RestClient / RestTemplate) 业务上下文透传拦截器
 */
@RequiredArgsConstructor
public class SpringWebTraceInterceptor implements ClientHttpRequestInterceptor {

  private final TraceContextProperties properties;

  @NotNull
  @Override
  public ClientHttpResponse intercept(
    @NotNull HttpRequest request,
    @NotNull byte[] body,
    @NotNull ClientHttpRequestExecution execution
  ) throws IOException {
    // 遍历配置中定义的 Header，从 MDC 取值并注入请求头
    for (String alias : properties.getAliases()) {
      String value = MDC.get(alias);
      if (value != null) {
        // 使用 add 防止覆盖原有 Header（虽然通常不会有），也可以用 set 强制覆盖
        request.getHeaders().add(properties.getHeaderKey(alias), value);
      }
    }
    return execution.execute(request, body);
  }
}
