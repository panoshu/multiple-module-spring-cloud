package com.example.shared.cache.core;

/**
 * 缓存同步策略接口 (策略模式)
 * 解耦具体的通知机制 (Redis/Kafka/RocketMQ)
 */
public interface CacheSyncPolicy {
  void publishEvict(String cacheName, Object key);

  void publishClear(String cacheName);

  /**
   * 空实现 (用于 L1_ONLY 或无 Redis 环境)
   */
  class NoOp implements CacheSyncPolicy {
    @Override
    public void publishEvict(String cacheName, Object key) {
    }

    @Override
    public void publishClear(String cacheName) {
    }
  }
}
