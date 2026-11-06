package com.example.shared.id.autoconfigure;

import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.DistributedLockFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 分布式锁兜底配置
 * 只有当容器中没有其他 DistributedLock 实现（如 Redisson）时，才生效。
 * Order 设置为最低优先级，确保 Redis 配置先执行。
 */
@AutoConfiguration(afterName = "com.example.shared.lock.autoconfigure.DistributedLockAutoConfiguration")
@Slf4j
public class DistributedLockConfiguration {

  // localDistributedLock 兜底已归口 shared-lock-starter 的 DistributedLockAutoConfiguration
  // （2026-11-06：本类经 @Import 嵌套不参与自动配置排序，兜底会抢占 Lock4j 锁的装配位）

  @Bean
  @ConditionalOnClass(DistributedLockFactory.class)
  public DistributedLockFactory distributedLockFactory(Map<String, DistributedLock> lockMap) {
    return new DistributedLockFactory(lockMap);
  }
}
