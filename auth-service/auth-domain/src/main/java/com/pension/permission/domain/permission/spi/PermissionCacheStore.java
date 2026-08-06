package com.pension.permission.domain.permission.spi;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;

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
}
