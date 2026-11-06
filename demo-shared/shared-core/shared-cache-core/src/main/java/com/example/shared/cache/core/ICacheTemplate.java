package com.example.shared.cache.core;

import java.util.Optional;
import java.util.function.Function;

/**
 * 统一缓存端口（业务侧唯一依赖点，六边形 port）.
 *
 * <p>业务代码（含领域层定义的缓存 SPI 的基础设施适配器）应依赖本接口，
 * 由 shared-cache-starter 装配实现（2026-11-05 起为
 * {@code JetCacheSharedCacheTemplate}，基于 JetCache 2.8）——换底层缓存
 * 框架、序列化策略、同步策略均不触及调用方.</p>
 *
 * <p>TTL 语义：由 CacheRule（{@code shared.cache.rules.<name>}.ttl）在缓存名级别
 * 配置，端口不做单键 TTL 覆盖（避免承诺各实现层都难以完整支持的语义）.</p>
 *
 * <p><b>防击穿约定</b>：优先使用 {@link #getOrLoad}（实现层原子"查不到→加载→回填"，
 * JetCache computeIfAbsent 单飞保护），避免"load→miss→save"两段式带来的
 * 并发击穿窗口；仅在计算逻辑跨层无法内联传递 loader 时才退回两段式.</p>
 */
public interface ICacheTemplate {

  void put(String cacheName, String key, Object value);

  <T> Optional<T> get(String cacheName, String key, Class<T> type);

  void evict(String cacheName, String key);

  /**
   * 清空指定缓存的全部条目（规则级全局变更失效场景）.
   *
   * <p>实现要求：本地层全部失效；远程层由该缓存的 TTL 兜底（JetCache
   * 为 key 级失效哲学，不提供遍历远程键的全清 API——避免 KEYS/SCAN 的代价）.
   * 业务全局失效（如 auth 权限码表变更清 auth-perm-cache 全量）应使用本方法，
   * 而不是自建 SCAN 遍历.</p>
   */
  void evictAll(String cacheName);

  /**
   * 获取或加载（原子防击穿，核心 API）.
   *
   * <p>实现层保证同一 key 并发 miss 时 loader 只执行一次（JetCache
   * computeIfAbsent / penetrationProtect 语义）.</p>
   *
   * @param loader 缓存未命中时的加载函数（入参为 key）
   */
  <T> T getOrLoad(String cacheName, String key, Function<String, T> loader, Class<T> type);

  /**
   * {@link #getOrLoad} 的便利重载（type 前置，更自然的调用顺序）.
   */
  default <T> T getOrLoad(String cacheName, String key, Class<T> type, Function<String, T> loader) {
    return getOrLoad(cacheName, key, loader, type);
  }
}
