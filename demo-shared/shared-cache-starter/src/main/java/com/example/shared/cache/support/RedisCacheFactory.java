package com.example.shared.cache.support;

import com.example.shared.cache.core.ICacheFactory;
import com.example.shared.cache.properties.SharedCacheProperties;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

public class RedisCacheFactory implements ICacheFactory {

  private final RedisCacheWriter redisCacheWriter;
  private final RedisSerializer<Object> valueSerializer;

  public RedisCacheFactory(RedisCacheWriter redisCacheWriter, RedisSerializer<Object> valueSerializer) {
    this.redisCacheWriter = redisCacheWriter;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public Cache createL1Cache(String name, SharedCacheProperties.CacheRule rule, SharedCacheProperties props) {
    throw new UnsupportedOperationException("RedisFactory cannot create L1 Cache");
  }

  @Override
  public Cache createL2Cache(String name, SharedCacheProperties.CacheRule rule, SharedCacheProperties props) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
      .entryTtl(rule.ttl())
      .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

    if (!rule.useKeyPrefix()) {
      config = config.disableKeyPrefix();
    }
    if (!rule.cacheNullValues()) {
      config = config.disableCachingNullValues();
    }

    // 使用 Builder 绕过 protected 构造函数
    return RedisCacheManager.builder(redisCacheWriter)
      .cacheDefaults(config)
      .build()
      .getCache(name);
  }
}
