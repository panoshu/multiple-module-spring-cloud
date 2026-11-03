package com.example.shared.lock;

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
    String beanName = type == LockType.LOCAL
        ? DistributedLock.LOCAL_BEAN_NAME
        : DistributedLock.REDISSON_BEAN_NAME;
    DistributedLock lock = lockMap.get(beanName);
    if (lock == null) {
      return lockMap.get(DistributedLock.LOCAL_BEAN_NAME);
    }
    return lock;
  }

  public enum LockType {
    LOCAL, REDIS
  }
}
