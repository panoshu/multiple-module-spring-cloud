package com.example.annuity;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 年金业务演示服务启动类（kernel starter 宿主示例）。
 *
 * <p><b>K-05 宿主契约</b>：本类不再声明
 * {@code scanBasePackages = "com.example.core"}，也不再扫描
 * {@code com.example.core.infrastructure.business.mapper}。
 * kernel 的组件、领域服务、自动配置、Mapper 与 {@code @EnableAsync}
 * 全部由 {@code business-core-starter} 统一装配（依赖见 annuity-starter POM）。</p>
 *
 * <p>{@code @MapperScan} 只保留本服务自己的 mapper 包；kernel mapper 由 starter 扫描。</p>
 *
 * <p>{@code @EnableExchangeClients} 注册审批/外接/权限远程 API 客户端代理；
 * file-api 客户端由 kernel 的 {@code FileServiceClientConfiguration} 按
 * {@code core.integration.file.base-url} 直接装配（避免与 httpexchange 代理重复定义，
 * 同时支持 file-service 不经注册中心的本地直连）。</p>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = {"com.example.approval.api", "com.example.integration.api", "com.example.auth.api"})
@SpringBootApplication
@MapperScan("com.example.annuity.infrastructure.mapper")
public class AnnuityApplication {

  public static void main(String[] args) {
    SpringApplication.run(AnnuityApplication.class, args);
  }
}
