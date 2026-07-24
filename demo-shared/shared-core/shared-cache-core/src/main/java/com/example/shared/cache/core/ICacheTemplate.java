package com.example.shared.cache.core;

import java.util.Optional;
import java.util.function.Function;

public interface ICacheTemplate {
  void put(String cacheName, String key, Object value);

  <T> Optional<T> get(String cacheName, String key, Class<T> type);

  void evict(String cacheName, String key);

  // 高级 API：获取或加载 (防击穿)
  <T> T getOrLoad(String cacheName, String key, Function<String, T> loader, Class<T> type);
}
