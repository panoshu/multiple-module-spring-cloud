package com.example.annuity;

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
 * 演示场景：基于 business-core-kernel 提供的 SPI 与聚合根，扩展年金专属业务扩展字段
 * （{@link com.example.annuity.domain.extension.AnnuityApplicationExtension}）和事实提取器
 * （{@link com.example.annuity.domain.extractor.AnnuityFactExtractor}），并通过 annuity-application
 * 编排 kernel 的 BusinessOrchestrationAppService。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.annuity", "com.example.core"})
public class AnnuityApplication {

  public static void main(String[] args) {
    SpringApplication.run(AnnuityApplication.class, args);
  }
}
