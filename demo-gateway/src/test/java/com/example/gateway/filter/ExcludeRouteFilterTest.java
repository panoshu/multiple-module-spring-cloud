package com.example.gateway.filter;

import com.example.gateway.config.ExcludeRouteProperties;
import com.example.gateway.matcher.ExcludeRouteMatcher;
import com.example.gateway.order.GatewayFilterOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcludeRouteFilterTest {

  @Mock
  private ExcludeRouteMatcher matcher;

  @Mock
  private ExcludeRouteProperties properties;

  @Mock
  private WebFilterChain chain;

  private ExcludeRouteFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ExcludeRouteFilter(matcher, properties);
  }

  @Test
  @DisplayName("测试 Order 优先级")
  void testOrder() {
    assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrder.EXCLUDE_ROUTE.value());
  }

  @Test
  @DisplayName("场景：未命中黑名单 -> 应该放行执行 Chain")
  void shouldPassThroughWhenNotMatched() {
    // Mock 没命中
    when(matcher.match(any(), any()))
      .thenReturn(new ExcludeRouteMatcher.MatchResult(false, null));

    // Mock Chain 继续执行
    when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

    // 构造请求
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/safe"));

    // 执行
    StepVerifier.create(filter.filter(exchange, chain))
      .verifyComplete();

    // 验证 chain.filter 被调用了
    verify(chain, times(1)).filter(exchange);
  }

  @Test
  @DisplayName("场景：命中黑名单 (自定义 Status) -> 应该拦截并返回指定 Status")
  void shouldBlockWithCustomStatus() {
    // Mock 命中，且规则定义了 404
    when(matcher.match(any(), any()))
      .thenReturn(new ExcludeRouteMatcher.MatchResult(true, 404));

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/bad"));

    // 执行
    StepVerifier.create(filter.filter(exchange, chain))
      .verifyComplete();

    // 验证
    verify(chain, never()).filter(any()); // 绝对不能调用 chain.filter
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("场景：命中黑名单 (无 Status) -> 应该拦截并返回全局默认 Status")
  void shouldBlockWithDefaultStatus() {
    // Mock 命中，但规则 Status 为 null
    when(matcher.match(any(), any()))
      .thenReturn(new ExcludeRouteMatcher.MatchResult(true, null));

    // Mock 全局默认配置为 403
    when(properties.defaultStatus()).thenReturn(403);

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/internal/api"));

    StepVerifier.create(filter.filter(exchange, chain))
      .verifyComplete();

    verify(chain, never()).filter(any());
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
