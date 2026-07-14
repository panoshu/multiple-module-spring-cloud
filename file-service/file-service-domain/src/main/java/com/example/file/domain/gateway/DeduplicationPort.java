package com.example.file.domain.gateway;

// ==========================================
// 3. 防重防腐层 (Deduplication Port)
// ==========================================
public interface DeduplicationPort {
  // 文件间重复校验：跨文件防重（如基于 Redis TTL）
  // 返回 true 表示已存在重复
  boolean checkAndLockInterFileDuplicate(String bizType, String uniqueKey, String ttl);
}
