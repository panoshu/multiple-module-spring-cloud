package com.example.file.infrastructure.excel;

import com.example.file.domain.gateway.DeduplicationPort;

// import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * RedisDeduplicationAdapter
 * 基于 Redis 的跨文件（跨系统）防重适配器
 */
public class RedisDeduplicationAdapter implements DeduplicationPort {

  // private final StringRedisTemplate redisTemplate;
  // public RedisDeduplicationAdapter(StringRedisTemplate redisTemplate) { this.redisTemplate = redisTemplate; }

  @Override
  public boolean checkAndLockInterFileDuplicate(String bizType, String uniqueKey, String ttl) {
    String redisKey = "gateway:dedup:" + bizType + ":" + uniqueKey;

    // 真实生产环境代码示例：
    // long ttlHours = Long.parseLong(ttl.replace("d", "")) * 24;
    // Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofHours(ttlHours));
    // return !Boolean.TRUE.equals(success); // 如果 setIfAbsent 返回 false，说明 key 已存在，即发生重复

    // 测试阶段默认返回不重复
    return false;
  }
}
