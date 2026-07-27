package com.example.gateway;


import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类。
 * <p>
 * 通过 {@code @EnableExchangeClients} 注册 {@code com.example.iam.api} 包下
 * 标注 {@code @HttpExchange} 的远程 API 客户端代理,通过 LoadBalancer 解析服务名
 * 调用 iam-service(如 {@code RouteRuleApi} 加载动态鉴权规则)。
 *
 * @author trae
 * @since 1.0
 */
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = {"com.example.iam.api"})
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
