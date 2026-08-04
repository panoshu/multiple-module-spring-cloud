package com.example.file.domain.gateway;

import java.time.Duration;

/**
 * 文件 Token 一次性使用标记 SPI
 * <p>
 * 由 RedisFileTokenStore 实现，基于 Redis SETNX + TTL。
 */
public interface FileTokenStore {

  /**
   * 标记 token 已使用
   *
   * @return true=首次标记成功, false=已存在（重复使用）
   */
  boolean markUsed(String tokenId, Duration ttl);

  /**
   * 检查 token 是否已使用
   */
  boolean isUsed(String tokenId);
}
