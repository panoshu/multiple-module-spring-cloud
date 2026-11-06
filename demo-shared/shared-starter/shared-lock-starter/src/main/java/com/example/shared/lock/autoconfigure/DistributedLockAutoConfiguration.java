package com.example.shared.lock.autoconfigure;

import com.baomidou.lock.LockTemplate;
import com.baomidou.lock.executor.LockExecutor;
import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.LocalDistributedLock;
import com.example.shared.lock.LocalLockExecutor;
import com.example.shared.lock.Lock4jDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分布式锁自动配置（lock4j 实现，2026-11-05 客户端统一治理）.
 *
 * <p>注册 {@link Lock4jDistributedLock}，bean 名为 {@code redisDistributedLock}，
 * 与 {@link com.example.shared.lock.DistributedLockFactory#getLock} 的查找契约一致.</p>
 *
 * <p>装配条件：容器存在 lock4j 的 {@link LockTemplate}（lock4j 自动配置在
 * Redis 可用时装配）。无 Redis 的宿主本配置不生效，由 shared-id-starter 的
 * 兜底配置注册 {@code localDistributedLock} 降级本地锁.</p>
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
   * 无 Redis 宿主兜底：给 lock4j 的 LockTemplate 提供本地 executor.
   *
   * <p>lock4j 无条件注册 LockTemplate 但要求至少一个 LockExecutor——
   * 无 Redis（如 annuity 测试上下文）时 RedisTemplateLockExecutor 不装配，
   * 没有本兜底会导致整个 Spring 上下文启动失败. 有 Redis 时本兜底让位
   * （条件互斥，不会静默降级）.</p>
   */
  @Bean
  @ConditionalOnClass(name = "com.baomidou.lock.executor.LockExecutor")
  @ConditionalOnMissingBean(LockExecutor.class)
  public LockExecutor<?> localLockExecutor() {
    log.info("Initialized LocalLockExecutor (无 Redis 宿主，lock4j LockTemplate 兜底)");
    return new LocalLockExecutor();
  }

  /**
   * lock4j 锁配置（隔离在内部类中，避免主类加载时因找不到 lock4j 类报错）.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.baomidou.lock.LockTemplate")
  static class Lock4jConfiguration {

    @Bean(DistributedLock.REDIS_BEAN_NAME)
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnBean(LockTemplate.class)
    public DistributedLock lock4jDistributedLock(LockTemplate lockTemplate) {
      log.info("Initialized Lock4jDistributedLock (RedisTemplate executor, Lettuce)");
      return new Lock4jDistributedLock(lockTemplate);
    }

    /**
     * 本地锁兜底：同类声明在 redis bean 之后——有 Redis 时上面的 bean 先注册
     * （ConditionalOnMissingBean 短路），本兜底跳过；无 Redis 时兜底接管.
     * <p>同类内 bean 按声明顺序处理，天然解决跨自动配置排序问题
     * （原 id-starter 兜底因 @Import 嵌套不参与排序而抢占装配位，2026-11-06 移入本类归口）.</p>
     */
    @Bean(DistributedLock.LOCAL_BEAN_NAME)
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock localDistributedLock() {
      log.info("Initialized LocalDistributedLock (Fallback, 无 Redis 宿主)");
      return new LocalDistributedLock();
    }
  }
}
