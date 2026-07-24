package com.example.shared.cache.service;

import com.example.shared.cache.core.ICacheTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.function.Function;

/**
 * 统一缓存服务默认实现
 * <p>
 * 职责：
 * 1. 作为 Facade 屏蔽底层 CacheManager 的细节。
 * 2. 将业务接口适配到 Spring Cache 标准 API。
 * 3. 统一处理异常和空值逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public class SharedCacheTemplate implements ICacheTemplate {

  private final CacheManager cacheManager;

  @Override
  public void put(String cacheName, String key, Object value) {
    Cache cache = getCacheOrThrow(cacheName);
    cache.put(key, value);
  }

  @Override
  public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
    Cache cache = getCacheOrThrow(cacheName);
    // 使用 Spring Cache 的 get(key, class) 方法，它会自动处理类型转换
    // 如果缓存中没有值，或者值为 null，则返回 null
    T value = cache.get(key, type);
    return Optional.ofNullable(value);
  }

  @Override
  public void evict(String cacheName, String key) {
    Cache cache = getCacheOrThrow(cacheName);
    cache.evict(key);
  }

  @Override
  public <T> T getOrLoad(String cacheName, String key, Function<String, T> loader, Class<T> type) {
    Cache cache = getCacheOrThrow(cacheName);

    // 【核心逻辑】委托给 Spring Cache 的原子加载方法
    // 1. 如果是 LayeredCache：内部集成了 DistributedLock，支持分布式防击穿。
    // 2. 如果是 CaffeineCache：内部使用 ConcurrentHashMap，支持本地防击穿。
    // 3. 如果是 RedisCache：Spring Data Redis 会处理并发加载（取决于 sync 配置）。
    try {
      return cache.get(key, () -> loader.apply(key));
    } catch (Cache.ValueRetrievalException e) {
      // 解包具体的业务异常，避免将框架异常抛给业务层
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException("Failed to load cache value for key: " + key, e.getCause());
    }
  }

  /**
   * 获取缓存实例，如果不存在则抛出异常
   * 在 DynamicCacheManager 的支持下，这里通常会动态创建缓存，极少抛异常
   */
  private Cache getCacheOrThrow(String cacheName) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      throw new IllegalStateException("Cache configuration not found for name: " + cacheName);
    }
    return cache;
  }
}
