package com.example.shared.lock.autoconfigure;

import com.baomidou.lock.LockTemplate;
import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.Lock4jDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 分布式锁自动配置（lock4j，2026-11-06 定位收敛）.
 *
 * <p><b>组件定位</b>：纯 Redis 分布式锁（lock4j RedisTemplate executor，
 * Lettuce 统一通道）。<b>Redis 是硬前提</b>，三层表达：
 * <ul>
 *   <li>依赖层：starter 显式依赖 spring-boot-starter-data-redis（非 optional）
 *       ——classpath 必有 Redis，RedisTemplateLockExecutor 必装配</li>
 *   <li>装配层：{@link Lock4jDistributedLock} 无条件注册（LockTemplate
 *       缺失/内部 executor 缺失 = 启动失败，fail-fast）</li>
 *   <li>连接层：启动期 PING 校验（{@code shared.lock.connection-check}，
 *       默认 true）——Redis 不可达即启动失败；测试环境可关闭或 mock</li>
 * </ul></p>
 *
 * <p><b>历史演进</b>：v1（Redisson + 本地锁降级 + Factory）→ v2（lock4j +
 * 无 Redis 宿主本地兜底）→ v3（本版，纯 Redis fail-fast）。本地锁与
 * Factory 已删除：分布式锁静默降级为本地锁会把架构决策藏进组件——
 * 无 Redis 的错误应显性暴露（2026-11-06 客户端治理定论）.</p>
 *
 * <p>lock4j 的声明式 {@code @Lock4j} 注解能力由其自身自动配置直接提供，
 * 业务可选用；编程式统一走 {@link DistributedLock} 契约.</p>
 *
 * @author panoshu
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(name = {
  "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
  "com.baomidou.lock.spring.boot.autoconfigure.RedisTemplateLockAutoConfiguration",
  "com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration"})
public class DistributedLockAutoConfiguration {

  /**
   * 锁实现：无条件注册（Redis 硬前提；结构缺失即启动失败）.
   */
  @Bean
  @ConditionalOnClass(name = "com.baomidou.lock.LockTemplate")
  @ConditionalOnMissingBean(DistributedLock.class)
  public DistributedLock lock4jDistributedLock(LockTemplate lockTemplate) {
    log.info("Initialized Lock4jDistributedLock (RedisTemplate executor, Lettuce)");
    return new Lock4jDistributedLock(lockTemplate);
  }

  /**
   * 启动期连接校验（fail-fast）：Redis 不可达直接启动失败.
   *
   * <p>与惰性连接（Spring Data Redis 默认）的取舍：本组件是"互斥正确性"
   * 的基础组件，连接缺失晚发现 = 运行期锁全部失效——宁可启动失败.
   * 测试环境关闭：{@code shared.lock.connection-check=false} 或 mock 连接.</p>
   */
  @Bean
  @ConditionalOnProperty(prefix = "shared.lock", name = "connection-check",
    havingValue = "true", matchIfMissing = true)
  public LockConnectionChecker lockConnectionChecker(RedisConnectionFactory factory) {
    return new LockConnectionChecker(factory);
  }

  /**
   * 连接校验器（ApplicationRunner：上下文就绪后、接流量前执行）.
   */
  @RequiredArgsConstructor
  static class LockConnectionChecker implements ApplicationRunner {

    private final RedisConnectionFactory factory;

    @Override
    public void run(ApplicationArguments args) {
      try (var conn = factory.getConnection()) {
        conn.ping();
        log.info("Lock Redis connection check passed");
      } catch (Exception e) {
        throw new IllegalStateException(
          "[shared-lock] Redis 不可达，分布式锁组件启动失败（fail-fast）——"
            + "请检查 Redis 连接；测试环境可设 shared.lock.connection-check=false", e);
      }
    }
  }
}
