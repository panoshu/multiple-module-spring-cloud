package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 基于 Redis SETNX + TTL 的一次性 token 标记实现。
 * <p>
 * 使用 Redisson {@link RBucket#setIfAbsent(Object, Duration)} 实现 SETNX 语义：
 * 首次 markUsed 返回 true，重复返回 false；TTL 自动过期释放。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisFileTokenStore implements FileTokenStore {

    private static final String MARKER_VALUE = "1";

    private final RedissonClient redissonClient;
    private final FileTokenProperties properties;

    @Override
    public boolean markUsed(String tokenId, Duration ttl) {
        String key = properties.getRedis().getKeyPrefix() + tokenId;
        RBucket<String> bucket = redissonClient.getBucket(key);
        boolean success = bucket.setIfAbsent(MARKER_VALUE, ttl);
        if (!success) {
            log.warn("token 重复使用: tokenId={}", tokenId);
        }
        return success;
    }

    @Override
    public boolean isUsed(String tokenId) {
        String key = properties.getRedis().getKeyPrefix() + tokenId;
        return redissonClient.getBucket(key).isExists();
    }
}
