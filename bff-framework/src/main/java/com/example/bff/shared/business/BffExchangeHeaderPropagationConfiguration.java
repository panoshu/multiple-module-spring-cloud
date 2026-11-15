package com.example.bff.shared.business;

import io.github.danielliu1123.httpexchange.HttpClientCustomizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * BFF → 业务服务的会话头透传（§9.3-③）。
 *
 * <p>httpexchange 3.5.5 不支持请求级动态 header；BFF 收到网关注入的签名头后，
 * 通过 RestClient 拦截器把同样的头复制到出站请求，业务服务的
 * {@code SessionContextResolver} 才能验签通过。</p>
 *
 * <p>只复制网关签发的会话头，不复制 Cookie/Authorization 等终端凭证。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class BffExchangeHeaderPropagationConfiguration {

  private static final List<String> SESSION_HEADERS = List.of(
    "X-Account-Id", "X-Account-Sig",
    "X-Session-Context", "X-Session-Sig", "X-Session-Expire");

  @Bean
  public HttpClientCustomizer.RestClientCustomizer bffSessionHeaderRestClientCustomizer() {
    return (builder, channel) -> builder.requestInterceptor((request, body, execution) -> {
      if (RequestContextHolder.getRequestAttributes()
          instanceof ServletRequestAttributes attributes) {
        HttpServletRequest inbound = attributes.getRequest();
        for (String header : SESSION_HEADERS) {
          String value = inbound.getHeader(header);
          if (value != null && !value.isBlank() && !request.getHeaders().containsKey(header)) {
            request.getHeaders().add(header, value);
          }
        }
      }
      log.debug("[BffHeaderPropagation] {} {} headers={}", request.getMethod(),
        request.getURI().getPath(), request.getHeaders().keySet());
      return execution.execute(request, body);
    });
  }
}
