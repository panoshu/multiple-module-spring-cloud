package com.example.shared.lock;

import com.baomidou.lock.LockTemplate;
import com.baomidou.lock.executor.RedisTemplateLockExecutor;
import com.baomidou.lock.LockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 lock4j（RedisTemplate executor）的分布式锁适配实现.
 *
 * <p><b>选型背景</b>（2026-11-05 Redis 客户端统一治理）：
 * <ul>
 *   <li>替换 Redisson（全仓仅用到 RLock+SETNX，24 连接起步的连接池与独立
 *       netty 线程模型过重）</li>
 *   <li>lock4j 的 RedisTemplate executor 走 Spring Data Redis（Lettuce 统一通道），
 *       自带成熟的自旋重试、防死锁过期、锁 key 前缀隔离、失败策略扩展</li>
 *   <li>声明式 {@code @Lock4j} 注解（SpEL key）对业务开放，编程式仍走
 *       {@link DistributedLock} 契约——两层并行不冲突</li>
 * </ul></p>
 *
 * <p><b>租期语义</b>：lock4j 的 RedisTemplate executor 无看门狗续期——
 * {@code leaseTime <= 0} 时使用 lock4j 默认租期（lock4j.default-expire，30s）.
 * 长临界区应显式传足额租期.</p>
 *
 * @author shared-lock-starter
 */
@Slf4j
@RequiredArgsConstructor
public class Lock4jDistributedLock implements DistributedLock {

  private final LockTemplate lockTemplate;

  @Override
  public Optional<LockHandle> tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
    long expireMs = leaseTime > 0 ? unit.toMillis(leaseTime) : -1L;
    long acquireMs = Math.max(0, unit.toMillis(waitTime));
    LockInfo info = lockTemplate.lock(key, expireMs, acquireMs, RedisTemplateLockExecutor.class);
    return info != null ? Optional.of(new Lock4jHandle(lockTemplate, info)) : Optional.empty();
  }

  @Override
  public <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
    Optional<LockHandle> handle = tryLock(key, waitTime, leaseTime, unit);
    return handle.map(h -> {
      try (LockHandle ignored = h) {
        return task.get();
      }
    }).orElseThrow(() -> new com.example.shared.exception.SystemException(
      com.example.shared.lock.errorcode.LockErrorDefinition.GET_LOCK_FAILED)
      .withLogDetail("Failed to acquire lock: " + key));
  }

  /**
   * lock4j 句柄（幂等释放，委托 LockTemplate.releaseLock）.
   */
  static final class Lock4jHandle implements LockHandle {

    private final LockTemplate lockTemplate;
    private final LockInfo lockInfo;
    private volatile boolean released = false;

    Lock4jHandle(LockTemplate lockTemplate, LockInfo lockInfo) {
      this.lockTemplate = lockTemplate;
      this.lockInfo = lockInfo;
    }

    @Override
    public void release() {
      if (released) {
        return;
      }
      released = true;
      boolean ok = lockTemplate.releaseLock(lockInfo);
      if (!ok) {
        log.warn("锁释放未成功（可能已过期被回收）, key: {}", lockInfo.getLockKey());
      }
    }
  }
}
