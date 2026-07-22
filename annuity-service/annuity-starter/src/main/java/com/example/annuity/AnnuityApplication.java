package com.example.annuity;

import com.example.core.application.engine.step.handler.FormParsingHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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
 * <p>
 * <b>【kernel bug 规避】</b>：kernel 同时存在 {@link FormParsingHandler}（旧实现，依赖 FormRepository）
 * 和 {@code DefaultFormParsingHandler}（新实现，依赖 BusinessConfigGateway），两者 {@code handlerName()}
 * 均返回 "defaultFormParsingHandler"，导致 {@code AbstractStrategyRegistry.register()} 抛同名冲突。
 * 此处通过 {@code @ComponentScan.excludeFilters} 排除旧实现，保留新实现作为唯一策略。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.annuity", "com.example.core"})
@MapperScan("com.example.annuity.infrastructure.mapper")
@ComponentScan(
    basePackages = {"com.example.annuity", "com.example.core"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = FormParsingHandler.class
    )
)
public class AnnuityApplication {

  public static void main(String[] args) {
    SpringApplication.run(AnnuityApplication.class, args);
  }
}
