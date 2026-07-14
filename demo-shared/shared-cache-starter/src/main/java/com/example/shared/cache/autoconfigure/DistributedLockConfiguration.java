package com.example.shared.cache.autoconfigure;

import com.example.shared.cache.lock.DistributedLock;
import com.example.shared.cache.lock.RedissonDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class DistributedLockConfiguration {

  /**
   * 1. Redisson 锁配置 (隔离在内部类中)
   * 只有当类路径下存在 RedissonClient 时，才会加载此内部类。
   * 避免了在主类加载时因为找不到 RedissonClient 参数类型而报错。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
  static class RedissonLockConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "shared.cache", name = "mode", havingValue = "L2_ONLY", matchIfMissing = true)
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock redissonLockL2(org.redisson.api.RedissonClient client) {
      return new RedissonDistributedLock(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shared.cache", name = "mode", havingValue = "MULTI")
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock redissonLockMulti(org.redisson.api.RedissonClient client) {
      return new RedissonDistributedLock(client);
    }
  }
}
