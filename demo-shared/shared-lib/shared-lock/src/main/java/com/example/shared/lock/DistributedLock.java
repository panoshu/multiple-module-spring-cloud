package com.example.shared.lock;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁端口（契约 v2，2026-11-06 纯净化）.
 *
 * <p><b>定位</b>：跨进程互斥原语——<b>只有 Redis 实现，没有本地降级</b>.
 * 分布式锁降级为进程内锁是危险的静默语义（多实例互斥消失），组件层不做此决策；
 * 无 Redis 的宿主不装配本接口，由使用方按自身语义决定：
 * <ul>
 *   <li>强依赖（正确性必需）：装配条件不满足，启动即失败（fail-fast 传导）</li>
 *   <li>弱依赖（单飞保护/防重）：装配条件不满足，相关组件自然不装配</li>
 * </ul></p>
 *
 * <p><b>语义约定</b>：
 * <ul>
 *   <li>{@code waitTime}：获取锁的自旋等待上限，0 表示立即失败</li>
 *   <li>{@code leaseTime}：锁持有租期（防持锁进程崩溃死锁）；{@code <= 0}
 *       表示使用实现默认租期（30s）。<b>无看门狗自动续期</b>——长临界区
 *       应显式传足额租期</li>
 *   <li>{@code lockAndRun}：模板方法，获取失败抛
 *       {@link com.example.shared.exception.SystemException}(GET_LOCK_FAILED)</li>
 * </ul></p>
 *
 * @author shared-lock
 */
public interface DistributedLock {

  /**
   * 尝试获取锁.
   *
   * @param key       锁标识（业务语义 key，实现层负责加前缀）
   * @param waitTime  获取等待上限（<=0 立即失败）
   * @param leaseTime 持有租期（<=0 使用默认 30s；无自动续期）
   * @param unit      时间单位
   * @return 成功返回句柄（用完必须 release，推荐 try-with-resources）；失败返回 empty
   */
  Optional<LockHandle> tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);

  /**
   * 模板方法：加锁执行任务并保证释放.
   *
   * <p>获取失败抛 {@link com.example.shared.exception.SystemException}.</p>
   */
  <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task);

  /**
   * 模板方法（默认等待 5s、默认租期）.
   */
  default <T> T lockAndRun(String key, Supplier<T> task) {
    return lockAndRun(key, 5, -1, TimeUnit.SECONDS, task);
  }
}
