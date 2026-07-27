package com.example.iam;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * IAM 服务上下文加载冒烟测试
 * <p>
 * 通过 {@code @ActiveProfiles("test")} 激活 H2 + 禁用 Nacos/Redis 的测试环境配置，
 * 验证 Spring 容器能够成功装载 IAM 服务的全部 Bean（domain/application/adapter/infrastructure）。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@SpringBootTest
@ActiveProfiles("test")
class IamApplicationSmokeTest {

  @Test
  void contextLoads() {
  }
}
