package com.example.core.infrastructure.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次 72：本地 Redis 连接配置验证（条件启用，日常构建跳过）。
 * <p>
 * 用于确认 {@code spring.data.redis.*} 的写法能真正连上本地 Redis —— 这是
 * {@code shared-lock-starter} 启动期 PING 校验与流程推进锁的前提。显式启用：
 * {@code KERNEL_REDIS_INTEGRATION=true mvn test -pl ... -Dtest=LocalRedisConnectionTest}
 */
@EnabledIfEnvironmentVariable(named = "KERNEL_REDIS_INTEGRATION", matches = "true")
@DisplayName("本地 Redis 连接配置验证（批次 72，条件启用）")
class LocalRedisConnectionTest {

  private ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
      .withPropertyValues(properties);
  }

  private void assertPing(ApplicationContextRunner runner) {
    runner.run(context -> {
      assertThat(context).hasNotFailed();
      RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);
      try (RedisConnection connection = factory.getConnection()) {
        assertThat(connection.ping()).isEqualTo("PONG");
      }
    });
  }

  @Test
  @DisplayName("无密码时连接 127.0.0.1:6379 并 PING 成功")
  void noPassword_pingSucceeds() {
    assertPing(runner("spring.data.redis.host=127.0.0.1",
      "spring.data.redis.port=6379", "spring.data.redis.database=0"));
  }

  @Test
  @DisplayName("带 password=redis 时连接 127.0.0.1:6379 并 PING")
  void withPassword_ping() {
    assertPing(runner("spring.data.redis.host=127.0.0.1",
      "spring.data.redis.port=6379", "spring.data.redis.database=0",
      "spring.data.redis.password=redis"));
  }
}
