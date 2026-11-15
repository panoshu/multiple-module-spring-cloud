package com.example.gateway.filter;

import com.example.gateway.order.GatewayFilterOrder;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 网关链路 ID 入口（V-05）。
 *
 * <p>网关是外部请求进入系统的第一跳：优先复用客户端/上游传入的 {@code X-Trace-Id}，
 * 缺失或非法时生成 32 位十六进制 ID，再写回下游请求头与响应头。下游 servlet 服务的
 * {@code BizContextPropagationFilter} 会把它放入 MDC，日志与跨服务客户端拦截器随之透传。</p>
 *
 * @author gateway-framework
 */
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  private static final String TRACE_RESPONSE_HEADER = "trace-id";
  private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  @NonNull
  public Mono<Void> filter(@NonNull ServerWebExchange exchange,
                           @NonNull GatewayFilterChain chain) {
    String traceId = resolveTraceId(exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER));

    ServerHttpRequest request = exchange.getRequest().mutate()
      .headers(headers -> headers.set(TRACE_ID_HEADER, traceId))
      .build();
    exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
    exchange.getResponse().getHeaders().set(TRACE_RESPONSE_HEADER, traceId);

    return chain.filter(exchange.mutate().request(request).build());
  }

  private String resolveTraceId(String candidate) {
    if (candidate != null && VALID_TRACE_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString().replace("-", "");
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.TRACE_ID.value();
  }
}
