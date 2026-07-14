package com.example.shared.cache.lock;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class DistributedLockFactory {

  private final Map<String, DistributedLock> lockMap;

  /**
   * 获取指定类型的锁
   *
   * @param type "local" 或 "redis"
   */
  public DistributedLock getLock(LockType type) {
    String beanName = type == LockType.LOCAL ? "localDistributedLock" : "redissonDistributedLock";
    DistributedLock lock = lockMap.get(beanName);
    if (lock == null) {
      // 降级策略：如果请求 Redis 锁但没配置 Redisson，降级为 Local 或者抛异常
      // 这里演示降级为 Local
      return lockMap.get("localDistributedLock");
    }
    return lock;
  }

  public enum LockType {
    LOCAL, REDIS
  }
}
