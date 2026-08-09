package com.example.gateway.filter;

import com.example.gateway.config.CryptoProperties;
import com.example.gateway.crypto.CryptoPolicy;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.json.processor.JsonFieldProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WebFlux 报文加密过滤器。
 *
 * <p>职责划分（SRP）：
 * <ul>
 *   <li>本类 — WebFlux 请求/响应 body 装饰、路由排除、contentType 判断</li>
 *   <li>{@link JsonFieldProcessor} — JSON 流式遍历 + 字段匹配 + 值替换</li>
 *   <li>{@link CryptoPolicy} — 提供加密/解密 {@link com.example.shared.json.action.FieldAction}</li>
 * </ul>
 *
 * <p>请求阶段：解密前端密文为明文，转发给后端。
 * <p>响应阶段：加密后端明文为密文，返回给前端。
 * <p>仅处理 Content-Type 为 application/json 的请求与响应。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoFilter implements WebFilter, Ordered {

  private final CryptoProperties properties;
  private final JsonFieldProcessor processor;
  private final CryptoPolicy cryptoPolicy;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Override
  @NonNull
  public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
    if (!properties.enabled() || properties.fields().isEmpty()) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getURI().getPath();
    if (isExcluded(path)) {
      return chain.filter(exchange);
    }
    if (!isJsonContentType(exchange.getRequest().getHeaders())) {
      return chain.filter(exchange);
    }
    return decryptRequestAndWrapResponse(exchange, chain);
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.CRYPTO.value();
  }

  private boolean isExcluded(String path) {
    return properties.excludePaths().stream()
      .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private boolean isJsonContentType(HttpHeaders headers) {
    MediaType contentType = headers.getContentType();
    return contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
  }

  private Mono<Void> decryptRequestAndWrapResponse(ServerWebExchange exchange, WebFilterChain chain) {
    return DataBufferUtils.join(exchange.getRequest().getBody())
      .flatMap(dataBuffer -> {
        String modifiedBody = decryptRequestBody(dataBuffer);
        byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
        ServerHttpRequest mutatedRequest = buildMutatedRequest(exchange, modifiedBytes);
        ServerHttpResponseDecorator responseDecorator = buildResponseDecorator(exchange);
        return chain.filter(exchange.mutate().request(mutatedRequest).response(responseDecorator).build());
      })
      .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
  }

  private String decryptRequestBody(DataBuffer dataBuffer) {
    byte[] bytes = new byte[dataBuffer.readableByteCount()];
    dataBuffer.read(bytes);
    DataBufferUtils.release(dataBuffer);
    String body = new String(bytes, StandardCharsets.UTF_8);
    return processor.process(body, cryptoPolicy.decryptAction());
  }

  private ServerHttpRequest buildMutatedRequest(ServerWebExchange exchange, byte[] modifiedBytes) {
    DataBuffer newBuffer = exchange.getResponse().bufferFactory().wrap(modifiedBytes);
    ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
      @Override
      @NonNull
      public Flux<DataBuffer> getBody() {
        return Flux.just(newBuffer);
      }
    };
    decorator.getHeaders().setContentLength(modifiedBytes.length);
    return decorator;
  }

  private ServerHttpResponseDecorator buildResponseDecorator(ServerWebExchange exchange) {
    return new ServerHttpResponseDecorator(exchange.getResponse()) {
      @Override
      @NonNull
      public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        if (!isJsonContentType(getHeaders())) {
          return super.writeWith(body);
        }
        return DataBufferUtils.join(body)
          .flatMap(dataBuffer -> {
            String encrypted = encryptResponseBody(dataBuffer);
            byte[] encryptedBytes = encrypted.getBytes(StandardCharsets.UTF_8);
            getHeaders().setContentLength(encryptedBytes.length);
            DataBuffer outBuffer = bufferFactory().wrap(encryptedBytes);
            return super.writeWith(Mono.just(outBuffer));
          })
          .switchIfEmpty(Mono.defer(() -> super.writeWith(body)));
      }
    };
  }

  private String encryptResponseBody(DataBuffer dataBuffer) {
    byte[] bytes = new byte[dataBuffer.readableByteCount()];
    dataBuffer.read(bytes);
    DataBufferUtils.release(dataBuffer);
    String body = new String(bytes, StandardCharsets.UTF_8);
    return processor.process(body, cryptoPolicy.encryptAction());
  }
}