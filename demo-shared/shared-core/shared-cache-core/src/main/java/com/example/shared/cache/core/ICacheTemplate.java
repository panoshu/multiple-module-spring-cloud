package com.example.shared.cache.core;

import java.util.Optional;
import java.util.function.Function;

/**
 * 统一缓存端口（业务侧唯一依赖点，六边形 port）.
 *
 * <p>业务代码（含领域层定义的缓存 SPI 的基础设施适配器）应依赖本接口，
 * 由 shared-cache-starter 装配 {@code SharedCacheTemplate} 实现——
 * 换 L1/L2 实现、序列化策略、同步策略均不触及调用方.
 *
 * <p>TTL 语义：由 CacheRule（{@code shared.cache.rules.<name>}.ttl）在缓存名级别
 * 配置，端口不做单键 TTL 覆盖（避免承诺各实现层都难以完整支持的语义）.
 */
public interface ICacheTemplate {
  void put(String cacheName, String key, Object value);

  <T> Optional<T> get(String cacheName, String key, Class<T> type);

  void evict(String cacheName, String key);

  /**
   * 清空指定缓存的全部条目（规则级全局变更失效场景）.
   *
   * <p>实现要求：L1/L2 双层同清（LayeredCache.clear 委托两级）；
   * 业务全局失效（如 auth 权限码表变更清 auth:perm 全量）应使用本方法，
   * 而不是自建 SCAN 遍历.
   */
  void evictAll(String cacheName);

  // 高级 API：获取或加载 (防击穿)
  <T> T getOrLoad(String cacheName, String key, Function<String, T> loader, Class<T> type);
}
