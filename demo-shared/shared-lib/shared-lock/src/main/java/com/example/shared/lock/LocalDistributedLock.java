package com.example.shared.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程内锁实现 (当未引入 Redisson 时使用)
 */
public class LocalDistributedLock implements DistributedLock {
  // 使用 ConcurrentHashMap 管理细粒度锁，注意：需定期清理未使用的锁以防内存泄漏
  // 这里做简化处理，生产环境建议引入 Caffeine 来存放 Lock 对象以实现自动过期
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
    ReentrantLock lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
    try {
      return lock.tryLock(waitTime, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public void unlock(String key) {
    ReentrantLock lock = locks.get(key);
    if (lock != null && lock.isHeldByCurrentThread()) {
      lock.unlock();
      // 简单清理策略：如果没线程等待了，尝试移除(存在并发小概率重建问题，但在本地锁场景可接受)
      // if (!lock.hasQueuedThreads()) locks.remove(key, lock);
    }
  }
}
