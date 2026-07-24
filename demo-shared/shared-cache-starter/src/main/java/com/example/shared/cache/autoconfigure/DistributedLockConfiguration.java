package com.example.shared.cache.autoconfigure;

import com.example.shared.cache.lock.DistributedLock;
import com.example.shared.cache.lock.RedissonDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分布式锁自动配置。
 * <p>
 * 注册 {@link RedissonDistributedLock}，bean 名为 {@code redissonDistributedLock}，
 * 与 {@link com.example.shared.cache.lock.DistributedLockFactory#getLock} 的查找契约一致。
 * <p>
 * 当类路径下不存在 RedissonClient 时，本配置不生效，
 * 由 shared-id-starter 的兜底配置注册 {@code localDistributedLock}。
 *
 * @author panoshu
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DistributedLockConfiguration {

  /**
   * Redisson 锁配置（隔离在内部类中，避免主类加载时因找不到 RedissonClient 报错）。
   * <p>
   * 合并原 redissonLockL2 / redissonLockMulti 两个方法：两者实现完全相同，
   * 区别仅在 cache.mode 条件。合并后 bean 名统一为 {@code redissonDistributedLock}，
   * 确保 DistributedLockFactory.getLock(REDIS) 能正确命中。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
  static class RedissonLockConfiguration {

    @Bean("redissonDistributedLock")
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock redissonDistributedLock(org.redisson.api.RedissonClient client) {
      log.info("Initialized RedissonDistributedLock");
      return new RedissonDistributedLock(client);
    }
  }
}
