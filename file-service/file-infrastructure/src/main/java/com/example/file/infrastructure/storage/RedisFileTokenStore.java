package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis SETNX + TTL 的一次性 token 标记实现。
 * <p>
 * 使用 {@link StringRedisTemplate#opsForValue()#setIfAbsent} 实现 SETNX 语义：
 * 首次 markUsed 返回 true，重复返回 false；TTL 自动过期释放。
 * <p>
 * 2026-11-05 客户端统一治理：Redisson RBucket 替换为 StringRedisTemplate
 * （等价语义，少一个重量级客户端依赖）。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisFileTokenStore implements FileTokenStore {

  private static final String MARKER_VALUE = "1";

  private final StringRedisTemplate redisTemplate;
  private final FileTokenProperties properties;

  @Override
  public boolean markUsed(String tokenId, Duration ttl) {
    String key = properties.getRedis().getKeyPrefix() + tokenId;
    Boolean success = redisTemplate.opsForValue().setIfAbsent(key, MARKER_VALUE, ttl);
    if (!Boolean.TRUE.equals(success)) {
      log.warn("token 重复使用: tokenId={}", tokenId);
    }
    return Boolean.TRUE.equals(success);
  }

  @Override
  public boolean isUsed(String tokenId) {
    String key = properties.getRedis().getKeyPrefix() + tokenId;
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }
}
