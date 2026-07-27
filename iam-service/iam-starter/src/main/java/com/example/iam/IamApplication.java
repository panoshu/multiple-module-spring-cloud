package com.example.iam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IAM 服务启动类
 * <p>
 * scanBasePackages 仅包含 {@code com.example.iam}（本服务），不包含 {@code com.example.core}，
 * 因为 IAM 服务不依赖 business-core-kernel，自身提供鉴权与权限领域能力。
 * <p>
 * {@code @MapperScan} 显式扫描 iam-infrastructure 的 BaseMapper 接口，
 * 使 MyBatis-Flex 注册 UserMapper / CredentialMapper / PermissionRuleMapper 等为 Bean。
 * <p>
 * 不使用 {@code @EnableExchangeClients}：IAM 服务不通过 httpexchange 调用外部服务 API，
 * 外部系统集成通过 Gateway 防腐层模式实现。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.iam"})
@MapperScan("com.example.iam.infrastructure.mapper")
public class IamApplication {

  public static void main(String[] args) {
    SpringApplication.run(IamApplication.class, args);
  }
}
