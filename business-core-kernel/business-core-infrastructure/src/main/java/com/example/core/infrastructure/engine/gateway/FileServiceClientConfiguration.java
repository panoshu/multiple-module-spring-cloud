package com.example.core.infrastructure.engine.gateway;

import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.ParsedDataApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * file-service HTTP 客户端装配（2026-11-08 批次2：补齐真实链路"最后一公里"）.
 *
 * <p><b>背景</b>：FileServiceIntegrationGateway 此前注入 FileTaskApi/FileAccessApi
 * 但全仓无任何 HttpExchange 代理 bean 定义——网关代码从未真正通电（annuity 靠
 * MockFileIntegrationGateway 掩盖）。本配置补齐客户端装配.</p>
 *
 * <p><b>装配条件</b>：{@code core.integration.file.base-url} 已配置——
 * 配置了走真实 HTTP 链路；未配置时 FileServiceIntegrationGateway 的条件装配
 * 让位（Mock 兜底，测试/演示环境行为不变）.</p>
 *
 * @author business-core-kernel
 */
@Slf4j
@Configuration
@ConditionalOnProperty("core.integration.file.base-url")
public class FileServiceClientConfiguration {

  @Bean
  public FileAccessApi fileAccessApi(RestClient.Builder restClientBuilder,
                                     FileServiceClientProperties properties) {
    return buildProxy(restClientBuilder, properties.getBaseUrl(), FileAccessApi.class);
  }

  @Bean
  public FileTaskApi fileTaskApi(RestClient.Builder restClientBuilder,
                                 FileServiceClientProperties properties) {
    return buildProxy(restClientBuilder, properties.getBaseUrl(), FileTaskApi.class);
  }

  @Bean
  public ParsedDataApi parsedDataApi(RestClient.Builder restClientBuilder,
                                     FileServiceClientProperties properties) {
    return buildProxy(restClientBuilder, properties.getBaseUrl(), ParsedDataApi.class);
  }

  private static <T> T buildProxy(RestClient.Builder builder, String baseUrl, Class<T> apiType) {
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(builder.baseUrl(baseUrl).build()))
      .build();
    log.info("file-service 客户端装配: {} -> {}", apiType.getSimpleName(), baseUrl);
    return factory.createClient(apiType);
  }
}
