package com.example.gateway;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 渠道网关启动类（单一代码库，按 profile 部署为不同入口）。
 *
 * <p>三个部署形态，差异全部由配置承载，代码零差异：
 * <ul>
 *   <li>{@code internet} profile —— 互联网入口：channels=[INTERNET]，路由到 internet-bff</li>
 *   <li>{@code intranet} profile —— 内网入口：channels=[HQ, BRANCH]，路由到 intranet-bff</li>
 *   <li>{@code dev} profile —— 开发联调入口：三渠道齐备，直连各业务服务</li>
 * </ul>
 *
 * <p>认证/加解密/会话注入/路由排除等公共能力复用 gateway-starter 组件
 * （同位于 {@code com.example.gateway} 包下）。每渠道独立 StpLogic，
 * 会话命名空间隔离（互联网会话与内网会话互不通用）。
 *
 * <p>通过 {@code @EnableExchangeClients} 注册 {@code com.example.auth.api} 包下
 * 标注 {@code @HttpExchange} 的远程 API 客户端代理，经 LoadBalancer 调用 auth-service。
 *
 * @author channel-gateway
 * @since 1.0
 */
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = {"com.example.auth.api"})
@SpringBootApplication(scanBasePackages = "com.example.gateway")
@ConfigurationPropertiesScan
public class ChannelGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChannelGatewayApplication.class, args);
  }
}
