package com.example.shared.cache.support;

import com.example.shared.cache.core.ICacheFactory;
import com.example.shared.cache.properties.SharedCacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

public class CaffeineCacheFactory implements ICacheFactory {

  @Override
  public Cache createL1Cache(String name, SharedCacheProperties.CacheRule rule, SharedCacheProperties props) {
    // 合并配置：优先用 Rule 配置，否则用全局 L1 配置
    int initialCapacity = rule.l1InitialCapacity() != 0 ? rule.l1InitialCapacity() : props.l1().initialCapacity();
    int maxSize = rule.l1MaximumSize() != 0 ? rule.l1MaximumSize() : props.l1().maxSize();

    Caffeine<Object, Object> builder = Caffeine.newBuilder()
      .initialCapacity(initialCapacity)
      .maximumSize(maxSize)
      .recordStats();

    if (rule.ttl() != null && !rule.ttl().isNegative()) {
      builder.expireAfterWrite(rule.ttl());
    }

    return new CaffeineCache(name, builder.build(), rule.cacheNullValues());
  }

  @Override
  public Cache createL2Cache(String name, SharedCacheProperties.CacheRule rule, SharedCacheProperties props) {
    throw new UnsupportedOperationException("CaffeineFactory cannot create L2 Cache");
  }
}
