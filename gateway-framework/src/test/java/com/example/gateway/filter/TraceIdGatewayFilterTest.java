package com.example.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V-05 网关 traceId 入口")
class TraceIdGatewayFilterTest {

  private final TraceIdGatewayFilter filter = new TraceIdGatewayFilter();

  @Test
  @DisplayName("复用上游 traceId 并透传下游与响应头")
  void reusesUpstreamTraceId() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/internet/bff/demo")
        .header(TraceIdGatewayFilter.TRACE_ID_HEADER, "trace-gw-1"));
    AtomicReference<String> forwarded = new AtomicReference<>();

    filter.filter(exchange, mutated -> {
      forwarded.set(mutated.getRequest().getHeaders()
        .getFirst(TraceIdGatewayFilter.TRACE_ID_HEADER));
      return Mono.empty();
    }).block();

    assertThat(forwarded.get()).isEqualTo("trace-gw-1");
    assertThat(exchange.getResponse().getHeaders().getFirst("x-trace-id"))
      .isEqualTo("trace-gw-1");
    assertThat(exchange.getResponse().getHeaders().getFirst("trace-id"))
      .isEqualTo("trace-gw-1");
  }

  @Test
  @DisplayName("缺少 traceId 时生成 32 位十六进制 ID")
  void generatesTraceIdWhenMissing() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/internet/bff/demo"));
    AtomicReference<String> forwarded = new AtomicReference<>();

    filter.filter(exchange, mutated -> {
      forwarded.set(mutated.getRequest().getHeaders()
        .getFirst(TraceIdGatewayFilter.TRACE_ID_HEADER));
      return Mono.empty();
    }).block();

    assertThat(forwarded.get()).matches("[0-9a-f]{32}");
    assertThat(exchange.getResponse().getHeaders().getFirst("x-trace-id"))
      .isEqualTo(forwarded.get());
  }

  @Test
  @DisplayName("非法 traceId 不被信任，重新生成")
  void regeneratesInvalidTraceId() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/internet/bff/demo")
        .header(TraceIdGatewayFilter.TRACE_ID_HEADER, "bad trace!"));
    AtomicReference<String> forwarded = new AtomicReference<>();

    filter.filter(exchange, mutated -> {
      forwarded.set(mutated.getRequest().getHeaders()
        .getFirst(TraceIdGatewayFilter.TRACE_ID_HEADER));
      return Mono.empty();
    }).block();

    assertThat(forwarded.get()).matches("[0-9a-f]{32}").isNotEqualTo("bad trace!");
  }
}
