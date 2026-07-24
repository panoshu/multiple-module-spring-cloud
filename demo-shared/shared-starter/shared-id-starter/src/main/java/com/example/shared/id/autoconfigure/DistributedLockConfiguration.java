package com.example.shared.id.autoconfigure;

import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.DistributedLockFactory;
import com.example.shared.lock.LocalDistributedLock;
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
@AutoConfiguration
@Slf4j
public class DistributedLockConfiguration {

  @Bean
  @ConditionalOnMissingBean(DistributedLock.class)
  public DistributedLock localDistributedLock() {
    log.info("Initialized LocalDistributedLock (Fallback)");
    return new LocalDistributedLock();
  }

  @Bean
  @ConditionalOnClass(DistributedLockFactory.class)
  public DistributedLockFactory distributedLockFactory(Map<String, DistributedLock> lockMap) {
    return new DistributedLockFactory(lockMap);
  }
}
