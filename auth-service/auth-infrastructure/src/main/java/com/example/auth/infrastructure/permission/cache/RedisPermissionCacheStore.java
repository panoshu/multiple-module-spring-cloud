package com.example.auth.infrastructure.permission.cache;

import com.example.core.types.UserNo;
import com.example.auth.domain.channel.valueobject.SessionPermissionCache;
import com.example.auth.domain.permission.spi.PermissionCacheStore;
import com.example.shared.cache.core.ICacheTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 基于 shared-cache 统一缓存端口的权限缓存存储适配器（使用方收编批次二）.
 *
 * <p>原实现直摸 StringRedisTemplate 自建 KV/TTL/序列化/SCAN 清空全套轮子——
 * 属于 shared-cache-starter 建成后零采用的典型存量。收编后：
 * <ul>
 *   <li>TTL 与存储模式由 CacheRule 配置（{@code shared.cache.rules.auth-perm-cache}，
 *       默认 L2_ONLY/300s，与原行为对等）</li>
 *   <li>序列化、防击穿、广播失效由框架承担（白得能力）</li>
 *   <li>全局清空（clearAll）映射 {@link ICacheTemplate#evictAll(String)}——
 *       替代原手写 SCAN 游标遍历</li>
 * </ul>
 * 领域 SPI（{@code PermissionCacheStore}）不变，业务零感知.
 *
 * @author auth-infrastructure
 */
@Slf4j
@Component
public class RedisPermissionCacheStore implements PermissionCacheStore {

  /**
   * 缓存名（对应 shared.cache.rules.auth-perm-cache 规则）.
   */
  static final String CACHE_NAME = "auth-perm-cache";

  private final ICacheTemplate cacheTemplate;

  public RedisPermissionCacheStore(ICacheTemplate cacheTemplate) {
    this.cacheTemplate = cacheTemplate;
  }

  @Override
  public Optional<SessionPermissionCache> load(UserNo accountId) {
    if (accountId == null) {
      return Optional.empty();
    }
    return cacheTemplate.get(CACHE_NAME, accountId.value(), SessionPermissionCache.class);
  }

  @Override
  public void save(UserNo accountId, SessionPermissionCache cache) {
    if (accountId == null || cache == null) {
      return;
    }
    cacheTemplate.put(CACHE_NAME, accountId.value(), cache);
    log.debug("保存权限缓存: accountId={}, cacheName={}", accountId.value(), CACHE_NAME);
  }

  @Override
  public void evict(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    cacheTemplate.evict(CACHE_NAME, accountId.value());
    log.debug("清除权限缓存: accountId={}", accountId.value());
  }

  @Override
  public void evictAll(Set<UserNo> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) {
      return;
    }
    for (UserNo accountId : accountIds) {
      evict(accountId);
    }
  }

  /**
   * 全局清空：经框架 evictAll（L1/L2 双清 + 广播失效），替代原手写 SCAN 遍历.
   */
  @Override
  public void clearAll() {
    cacheTemplate.evictAll(CACHE_NAME);
    log.info("全局清除权限缓存: cacheName={}", CACHE_NAME);
  }
}
