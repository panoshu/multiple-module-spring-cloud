package com.example.auth.domain.permission.spi;

import com.example.core.types.UserNo;
import com.example.auth.domain.channel.valueobject.SessionPermissionCache;

import java.util.Optional;
import java.util.Set;

/**
 * 权限缓存存储 SPI 端口。
 * <p>基础设施层提供 Redis 实现（{@code RedisPermissionCacheStore}），
 * 复用 Sa-Token 引入的 StringRedisTemplate 基础设施。
 * <p>缓存 Key 设计：{@code auth:perm:cache:{accountId}}，TTL 默认 5 分钟。
 */
public interface PermissionCacheStore {

  Optional<SessionPermissionCache> load(UserNo accountId);

  void save(UserNo accountId, SessionPermissionCache cache);

  void evict(UserNo accountId);

  void evictAll(Set<UserNo> accountIds);

  /**
   * 清空全部权限缓存（权限码表/角色模板全局变更时使用）.
   *
   * <p>实现应采用 SCAN 游标遍历（避免 KEYS 阻塞生产 Redis）。默认不支持时
   * 由调用方回退到 TTL 兜底并记录 WARN.
   */
  default void clearAll() {
    throw new UnsupportedOperationException("当前 PermissionCacheStore 实现不支持全局清空，回退 TTL 兜底");
  }
}
