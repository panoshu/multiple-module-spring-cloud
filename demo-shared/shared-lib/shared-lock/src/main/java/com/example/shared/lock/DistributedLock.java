package com.example.shared.lock;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁端口（2026-11-05 契约重构 v2）.
 *
 * <p><b>契约演进</b>：v1 的 {@code unlock(key)} 无句柄——释放安全性全靠实现层
 * 猜测（ThreadLocal 持有者模拟 / Redisson 线程绑定），跨线程语义含糊。
 * v2 改为<b>显式句柄</b>：{@link #tryLock} 成功返回 {@link LockHandle}，
 * 释放只对该句柄有效（幂等、防误删他人的锁），生命周期清晰.</p>
 *
 * <p><b>实现</b>：lock4j（RedisTemplate executor，Lettuce 统一通道）+
 * 本地降级实现。业务代码只依赖本接口.</p>
 *
 * <p><b>语义约定</b>：
 * <ul>
 *   <li>{@code waitTime}：获取锁的自旋等待上限，0 表示立即失败</li>
 *   <li>{@code leaseTime}：锁持有租期（防持锁进程崩溃死锁）；{@code <= 0}
 *       表示使用实现默认租期（30s）。<b>无看门狗自动续期</b>——长临界区
 *       应显式传足额租期</li>
 *   <li>{@code lockAndRun}：模板法，获取失败抛
 *       {@link com.example.shared.exception.SystemException}(GET_LOCK_FAILED)</li>
 * </ul></p>
 *
 * @author shared-lock
 */
public interface DistributedLock {

  String LOCAL_BEAN_NAME = "localDistributedLock";
  String REDIS_BEAN_NAME = "redisDistributedLock";

  /**
   * 尝试获取锁.
   *
   * @param key       锁标识（业务语义 key，实现层负责加前缀）
   * @param waitTime  获取等待上限（<=0 立即失败）
   * @param leaseTime 持有租期（<=0 使用默认 30s；无自动续期）
   * @param unit      时间单位
   * @return 成功返回句柄（用完必须 release/close，推荐 try-with-resources）；失败返回 empty
   */
  Optional<LockHandle> tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);

  /**
   * 模板方法：加锁执行任务并保证释放.
   *
   * <p>获取失败抛 {@link com.example.shared.exception.SystemException}.</p>
   */
  <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task);

  /**
   * 模板方法（默认租期）.
   */
  default <T> T lockAndRun(String key, Supplier<T> task) {
    return lockAndRun(key, 5, -1, TimeUnit.SECONDS, task);
  }
}
