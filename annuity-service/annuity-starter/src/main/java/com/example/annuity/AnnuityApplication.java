package com.example.annuity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 年金业务演示服务启动类
 * <p>
 * scanBasePackages 同时包含 {@code com.example.annuity}（本服务）和 {@code com.example.core}
 * （kernel 基础设施：DomainServiceConfiguration、LibJacksonModule、IntegrationEventSimulator 等），
 * 确保 kernel 提供的 @DomainService / @Component / @Configuration 能被正确扫描和注册。
 * <p>
 * {@code @MapperScan} 显式扫描 annuity-infrastructure 的 BaseMapper 接口，
 * 使 MyBatis-Flex 注册 FormMapper / BatchMapper / ApplicationMapper 为 Bean。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.annuity", "com.example.core"})
@MapperScan("com.example.annuity.infrastructure.mapper")
public class AnnuityApplication {

  public static void main(String[] args) {
    SpringApplication.run(AnnuityApplication.class, args);
  }
}
