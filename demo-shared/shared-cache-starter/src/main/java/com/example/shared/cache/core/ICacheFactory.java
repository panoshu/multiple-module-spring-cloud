package com.example.shared.cache.core;

import com.example.shared.cache.properties.SharedCacheProperties;
import com.example.shared.cache.properties.SharedCacheProperties.CacheRule;
import org.springframework.cache.Cache;

public interface ICacheFactory {
  Cache createL1Cache(String name, CacheRule rule, SharedCacheProperties properties);

  Cache createL2Cache(String name, CacheRule rule, SharedCacheProperties properties);
}
