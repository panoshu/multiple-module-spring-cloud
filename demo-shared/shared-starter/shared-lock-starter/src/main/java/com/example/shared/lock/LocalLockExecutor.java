package com.example.shared.lock;

import com.baomidou.lock.executor.LockExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * lock4j 的本地锁 executor 兜底（无 Redis 宿主）.
 *
 * <p><b>为什么需要</b>：lock4j 的 {@code LockAutoConfiguration} 无条件注册
 * {@code LockTemplate}，其 {@code afterPropertiesSet()} 要求 executors 至少
 * 一个（"executors must have at least one"）。而无 Redis 的宿主（如 annuity
 * 测试上下文显式排除 RedisAutoConfiguration）不会装配
 * {@code RedisTemplateLockExecutor}——没有本兜底，整个 Spring 上下文启动失败.
 *
 * <p><b>装配条件</b>（见 {@code DistributedLockAutoConfiguration}）：
 * 容器无 {@code StringRedisTemplate} 时注册；有 Redis 时由 lock4j 的
 * RedisTemplate executor 接管（条件互斥，不会静默降级为本地锁）.</p>
 *
 * <p>锁语义：进程内细粒度 ReentrantLock；acquire 返回锁实例（非 null 即成功），
 * releaseLock 校验当前线程持有后释放。等待重试由 lock4j LockTemplate 的
 * retryInterval 承担（本 executor 获取为立即尝试，无阻塞）.</p>
 *
 * @author shared-lock-starter
 */
public class LocalLockExecutor implements LockExecutor<ReentrantLock> {

  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public ReentrantLock acquire(String key, String value, long expire, long acquireTimeout) {
    // expire 在本地锁下无死锁风险（进程崩溃即释放），忽略
    ReentrantLock lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
    return lock.tryLock() ? lock : null;
  }

  @Override
  public boolean releaseLock(String key, String value, ReentrantLock lockInstance) {
    if (lockInstance == null || !lockInstance.isHeldByCurrentThread()) {
      return false;
    }
    lockInstance.unlock();
    return true;
  }
}
