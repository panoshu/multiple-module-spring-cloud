package com.example.shared.lock;

/**
 * 锁句柄（2026-11-05 契约重构 v2）.
 *
 * <p>{@link DistributedLock#tryLock} 成功的返回物——持有者凭句柄释放锁，
 * 幂等且仅释放自己持有的锁（token 校验），替代 v1 无句柄的
 * {@code unlock(key)} 含糊语义.</p>
 *
 * <p>用法（AutoCloseable，推荐 try-with-resources）：
 * <pre>{@code
 * try (LockHandle h = lock.tryLock("biz:key", 3, 30, SECONDS).orElseThrow(...)) {
 *     // 临界区
 * }
 * }</pre></p>
 *
 * @author shared-lock
 */
public interface LockHandle extends AutoCloseable {

  /**
   * 释放锁（幂等：重复调用安全；仅释放自己持有的锁）.
   */
  void release();

  @Override
  default void close() {
    release();
  }
}
