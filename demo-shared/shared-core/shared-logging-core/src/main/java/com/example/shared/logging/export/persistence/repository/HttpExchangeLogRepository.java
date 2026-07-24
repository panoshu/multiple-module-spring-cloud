package com.example.shared.logging.export.persistence.repository;

import com.example.shared.logging.core.model.HttpExchangeLog;

/**
 * HTTP 交换日志存储库接口
 */
public interface HttpExchangeLogRepository {
  /**
   * 插入或更新日志（幂等）
   */
  void upsert(HttpExchangeLog httpExchangeLog);
}
