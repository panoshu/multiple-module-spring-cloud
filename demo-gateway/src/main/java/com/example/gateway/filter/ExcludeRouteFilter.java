package com.example.gateway.filter;

import com.example.gateway.config.ExcludeRouteProperties;
import com.example.gateway.matcher.ExcludeRouteMatcher;
import com.example.gateway.order.GatewayFilterOrder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExcludeRouteFilter implements WebFilter, Ordered {

  private final ExcludeRouteMatcher matcher;
  private final ExcludeRouteProperties properties;

  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {

    String path = exchange.getRequest().getURI().getPath();
    String method = String.valueOf(exchange.getRequest().getMethod());

    var result = matcher.match(path, method);

    if (result.matched()) {
      return handleMatched(exchange, result);
    }

    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.EXCLUDE_ROUTE.value();
  }

  @NonNull
  private Mono<Void> handleMatched(ServerWebExchange exchange, ExcludeRouteMatcher.MatchResult result) {
    int status = Optional.ofNullable(result.ruleStatus()).orElse(properties.defaultStatus());
    exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
    return exchange.getResponse().setComplete();
  }
}
