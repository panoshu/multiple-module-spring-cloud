package com.example.shared.cache.core;

import java.io.Serializable;

public record CacheEvictMessage(
  String cacheName,
  Object key,
  String sourceInstanceId
) implements Serializable {
}
