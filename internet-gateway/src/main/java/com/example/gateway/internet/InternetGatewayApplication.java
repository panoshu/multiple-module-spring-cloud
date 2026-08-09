package com.example.gateway.internet;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 互联网网关启动类。
 *
 * <p>仅启用 INTERNET 渠道，路由到 internet-bff。认证/加解密/会话注入等公共能力
 * 复用 gateway-shared 组件（同位于 com.example.gateway 包下）。
 *
 * <p>通过 {@code @EnableExchangeClients} 注册 {@code com.example.auth.api} 包下
 * 标注 {@code @HttpExchange} 的远程 API 客户端代理，经 LoadBalancer 调用 auth-service。
 *
 * @author demo-gateway
 * @since 1.0
 */
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = {"com.example.auth.api"})
@SpringBootApplication(scanBasePackages = "com.example.gateway")
@ConfigurationPropertiesScan
public class InternetGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(InternetGatewayApplication.class, args);
  }
}