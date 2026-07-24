package com.example.shared.cache.core;

import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.DistributedLockFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LayeredCache extends AbstractValueAdaptingCache {

  private final String name;
  private final Cache l1Cache;
  private final Cache l2Cache;
  private final CacheSyncPolicy syncPolicy;
  private final DistributedLockFactory lockFactory;

  public LayeredCache(String name, Cache l1Cache, Cache l2Cache,
                      CacheSyncPolicy syncPolicy, boolean allowNullValues,
                      DistributedLockFactory lockFactory) {
    super(allowNullValues);
    this.name = name;
    this.l1Cache = l1Cache;
    this.l2Cache = l2Cache;
    this.syncPolicy = syncPolicy;
    this.lockFactory = lockFactory;
  }

  @Override
  @NonNull
  public String getName() {
    return name;
  }

  @Override
  @NonNull
  public Object getNativeCache() {
    return this;
  }

  @Override
  protected Object lookup(@NonNull Object key) {
    // 1. L1 Hit
    Object l1Value = l1Cache.get(key, Object.class);
    if (l1Value != null) {
      return l1Value;
    }

    // 2. L2 Hit
    Object l2Value = l2Cache.get(key, Object.class);
    if (l2Value != null) {
      l1Cache.put(key, l2Value); // Backfill L1
      return l2Value;
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
    // L1 Check
    Object l1Value = l1Cache.get(key, Object.class);
    if (l1Value != null) {
      return (T) l1Value;
    }

    // L2 Check with Distributed Lock (Anti-Stampede)
    // 使用分布式锁保证只有一个实例去查 DB 并回填
    String lockKey = "lock:cache:" + name + ":" + key;
    DistributedLock lock = lockFactory.getLock(DistributedLockFactory.LockType.REDIS); // 优先用 Redis 锁

    // 尝试加锁运行 (如果没配置 Redis 锁，Factory 会降级为 Local 锁)
    return lock.lockAndRun(lockKey, 30, 10, TimeUnit.SECONDS, () -> {
      // Double Check L2 (Maybe populated by another thread)
      Object l2Retry = l2Cache.get(key, Object.class);
      if (l2Retry != null) {
        l1Cache.put(key, l2Retry);
        return (T) l2Retry;
      }

      // Load from DB
      T loaded = l2Cache.get(key, valueLoader);
      if (loaded != null) {
        l1Cache.put(key, loaded);
      }
      return loaded;
    });
  }

  @Override
  public void put(@NonNull Object key, Object value) {
    l2Cache.put(key, value);
    l1Cache.put(key, value);
    syncPolicy.publishEvict(name, key);
  }

  @Override
  public void evict(@NonNull Object key) {
    l2Cache.evict(key);
    l1Cache.evict(key);
    syncPolicy.publishEvict(name, key);
  }

  @Override
  public void clear() {
    l2Cache.clear();
    l1Cache.clear();
    syncPolicy.publishClear(name);
  }
}
