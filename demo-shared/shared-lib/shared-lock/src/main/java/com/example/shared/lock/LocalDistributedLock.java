package com.example.shared.lock;

import com.example.shared.exception.SystemException;
import com.example.shared.lock.errorcode.LockErrorDefinition;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 进程内锁实现（无 Redis 环境的降级实现，2026-11-05 契约 v2 适配）.
 *
 * <p>使用 ConcurrentHashMap 管理细粒度 ReentrantLock（可重入——本地实现
 * 的天然能力，Redis 实现不承诺可重入）。句柄内绑定持有线程，跨线程
 * release 记 WARN 忽略（与 v1 isHeldByCurrentThread 语义一致）.</p>
 */
public class LocalDistributedLock implements DistributedLock {

  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public Optional<LockHandle> tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
    ReentrantLock lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
    try {
      // leaseTime 在本地锁下无死锁风险（进程崩溃即释放），忽略
      boolean ok = lock.tryLock(Math.max(0, waitTime), unit);
      return ok ? Optional.of(new LocalHandle(lock)) : Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  @Override
  public <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
    Optional<LockHandle> handle = tryLock(key, waitTime, leaseTime, unit);
    if (handle.isEmpty()) {
      throw new SystemException(LockErrorDefinition.GET_LOCK_FAILED)
        .withLogDetail("Failed to acquire lock: " + key);
    }
    try (LockHandle h = handle.get()) {
      return task.get();
    }
  }

  /**
   * 绑定持有线程的本地句柄（幂等释放）.
   */
  static final class LocalHandle implements LockHandle {

    private final ReentrantLock lock;
    private final Thread owner = Thread.currentThread();
    private volatile boolean released = false;

    LocalHandle(ReentrantLock lock) {
      this.lock = lock;
    }

    @Override
    public void release() {
      if (released) {
        return;
      }
      released = true;
      if (Thread.currentThread() != owner) {
        return; // 跨线程释放本地锁不合法，静默忽略（与 isHeldByCurrentThread 语义一致）
      }
      lock.unlock();
    }
  }
}
