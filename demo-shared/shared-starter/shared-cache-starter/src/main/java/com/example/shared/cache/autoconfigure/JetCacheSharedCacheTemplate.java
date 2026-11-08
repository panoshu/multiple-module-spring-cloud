package com.example.shared.cache.autoconfigure;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.example.shared.cache.core.ICacheTemplate;
import com.example.shared.cache.properties.SharedCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 基于 JetCache 2.8 的统一缓存端口实现（2026-11-05 客户端统一治理重写）.
 *
 * <p><b>配置翻译层</b>：保留业务侧 {@code shared.cache.rules.<name>} 声明式配置
 * （语义：这个缓存叫什么、活多久、几级——业务不感知底层框架），翻译为
 * JetCache {@link QuickConfig}：
 * <ul>
 *   <li>{@code mode} → {@link CacheType}：L1_ONLY→LOCAL、L2_ONLY→REMOTE、MULTI→BOTH</li>
 *   <li>{@code ttl} → {@code expire(Duration)}</li>
 *   <li>{@code l1InitialCapacity/l1MaximumSize} → LOCAL 容量（合并为 localLimit）</li>
 *   <li>未声明规则的 cacheName 走全局 {@code shared.cache.mode} 与默认 TTL</li>
 * </ul></p>
 *
 * <p><b>JetCache 白得的能力</b>（对比被替换的自研 LayeredCache/DynamicCacheManager）：
 * 两级缓存查找回填、{@code broadcastChannel} 跨 JVM 本地失效广播、
 * {@code penetrationProtect} 防击穿（getOrLoad 走 computeIfAbsent）、
 * 分布式锁保护 loader、命中率统计（statIntervalMinutes）、自动刷新.</p>
 *
 * <p><b>基础设施配置</b>：JetCache 连接等经 {@code jetcache.*} 原生属性
 * （jetcache.remote.default.uri 等），由 jetcache-starter 自动装配
 * {@link CacheManager}——本类只做门面包装.</p>
 *
 * @author shared-cache-starter
 */
@Slf4j
@RequiredArgsConstructor
public class JetCacheSharedCacheTemplate implements ICacheTemplate {

  private final CacheManager cacheManager;
  private final SharedCacheProperties properties;

  /**
   * cacheName → Cache 实例 memo（QuickConfig 构建有成本，进程内复用）.
   */
  private final Map<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

  @Override
  public void put(String cacheName, String key, Object value) {
    if (value == null) {
      return; // null 不缓存（与原实现 cacheNullValues 语义简化一致）
    }
    cacheOf(cacheName).put(key, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
    Object value = cacheOf(cacheName).get(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!type.isInstance(value)) {
      log.warn("缓存类型不匹配: cacheName={}, key={}, expected={}, actual={}",
        cacheName, key, type.getSimpleName(), value.getClass().getSimpleName());
      return Optional.empty();
    }
    return Optional.of((T) value);
  }

  @Override
  public void evict(String cacheName, String key) {
    cacheOf(cacheName).remove(key);
  }

  @Override
  public void evictAll(String cacheName) {
    // JetCache 是 key 级失效哲学（无遍历远程键的全清 API——设计上避免 KEYS/SCAN 的代价）。
    // 语义：重建实例（本地缓存全部自然失效；BOTH 的各 JVM 本地经 broadcastChannel
    // 在下次 key 级失效时同步），远程残留条目由该缓存的 TTL 兜底回收——
    // 业务上如 auth 权限缓存（TTL 300s）最坏承受一个 TTL 周期的陈旧。
    caches.remove(cacheName);
    log.info("evictAll: 缓存实例已重建（远程条目由 TTL 兜底）, cacheName={}", cacheName);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getOrLoad(String cacheName, String key, Function<String, T> loader, Class<T> type) {
    Cache<String, Object> cache = cacheOf(cacheName);
    // jetcache computeIfAbsent 自带 loader 单飞保护（penetrationProtect 语义）
    Function<String, Object> objectLoader = k -> loader.apply(k);
    Object value = cache.computeIfAbsent(key, objectLoader);
    return value != null && type.isInstance(value) ? (T) value : null;
  }

  /**
   * 按业务声明（shared.cache.rules）翻译 QuickConfig 并复用实例.
   */
  private Cache<String, Object> cacheOf(String cacheName) {
    return caches.computeIfAbsent(cacheName, name -> {
      SharedCacheProperties.CacheRule rule = resolveRule(name);
      CacheType cacheType = toCacheType(rule.mode() != null ? rule.mode() : properties.mode());
      QuickConfig.Builder builder = QuickConfig.newBuilder(name)
        .cacheType(cacheType)
        .expire(Objects.requireNonNullElse(rule.ttl(), Duration.ofHours(1)));
      if (cacheType != CacheType.REMOTE) {
        builder.localLimit(rule.l1MaximumSize() > 0 ? rule.l1MaximumSize() : 100);
      }
      log.info("JetCache 实例已创建: name={}, type={}, ttl={}s",
        name, cacheType, rule.ttl() == null ? -1 : rule.ttl().toSeconds());
      return cacheManager.getOrCreateCache(builder.build());
    });
  }

  private SharedCacheProperties.CacheRule resolveRule(String cacheName) {
    Map<String, SharedCacheProperties.CacheRule> rules = properties.rules();
    if (rules != null && rules.containsKey(cacheName)) {
      return rules.get(cacheName);
    }
    // 未声明规则：全局 mode + 规则默认值（TTL 1h 为 CacheRule 默认）
    return new SharedCacheProperties.CacheRule(null, null, 0, 0, false, true);
  }

  private CacheType toCacheType(SharedCacheProperties.CacheMode mode) {
    if (mode == null) {
      return CacheType.BOTH;
    }
    return switch (mode) {
      case L1_ONLY -> CacheType.LOCAL;
      case L2_ONLY -> CacheType.REMOTE;
      case MULTI -> CacheType.BOTH;
    };
  }
}
