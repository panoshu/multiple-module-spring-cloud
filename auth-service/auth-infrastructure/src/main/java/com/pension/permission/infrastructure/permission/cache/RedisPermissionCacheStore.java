package com.pension.permission.infrastructure.permission.cache;

import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 Redis 的权限缓存存储实现。
 * <p>复用 Sa-Token 引入的 StringRedisTemplate 基础设施。
 * <p>Key 设计：{@code auth:perm:cache:{accountId}}，存储 SessionPermissionCache JSON。
 */
@Slf4j
@Component
public class RedisPermissionCacheStore implements PermissionCacheStore {

  private static final String KEY_PREFIX = "auth:perm:cache:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  public RedisPermissionCacheStore(
    StringRedisTemplate redisTemplate,
    @Value("${auth.permission.cache.ttl-seconds:300}") long ttlSeconds
  ) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  @Override
  public Optional<SessionPermissionCache> load(UserNo accountId) {
    if (accountId == null) {
      return Optional.empty();
    }
    String json = redisTemplate.opsForValue().get(keyOf(accountId));
    return Optional.ofNullable(deserialize(json));
  }

  @Override
  public void save(UserNo accountId, SessionPermissionCache cache) {
    if (accountId == null || cache == null) {
      return;
    }
    String json = serialize(cache);
    redisTemplate.opsForValue().set(keyOf(accountId), json, ttl);
    log.debug("保存权限缓存: accountId={}, ttl={}s", accountId.value(), ttl.getSeconds());
  }

  @Override
  public void evict(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    redisTemplate.delete(keyOf(accountId));
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

  private String keyOf(UserNo accountId) {
    return KEY_PREFIX + accountId.value();
  }

  private String serialize(SessionPermissionCache cache) {
    try {
      return objectMapper.writeValueAsString(cache);
    } catch (Exception e) {
      throw new IllegalStateException("SessionPermissionCache 序列化失败", e);
    }
  }

  private SessionPermissionCache deserialize(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, SessionPermissionCache.class);
    } catch (Exception e) {
      log.error("SessionPermissionCache 反序列化失败: {}", e.getMessage(), e);
      return null;
    }
  }
}
