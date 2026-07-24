package com.example.shared.id.segment.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 号段模型 (不可变数据载体)
 * 内部维护 AtomicLong 以保证段内生成的线程安全
 *
 * @param minId      当前段起始 ID (不包含)
 * @param maxId      当前段最大 ID (包含)
 * @param step       步长
 * @param currentId  当前游标
 * @param createTime 创建时间
 */
public record IdSegment(
  long minId,
  long maxId,
  long step,
  AtomicLong currentId,
  Instant createTime
) {
  // 构造新段
  public IdSegment(long minId, long maxId, long step) {
    this(minId, maxId, step, new AtomicLong(minId), Instant.now());
  }

  /**
   * 尝试获取下一个 ID
   *
   * @return ID，如果本段用尽则返回 null
   */
  public Long next() {
    long id = currentId.incrementAndGet();
    if (id > maxId) {
      return null; // 段已用尽
    }
    return id;
  }

  /**
   * 获取使用率 (用于监控或未来的异步预加载)
   */
  public double getUsage() {
    long current = currentId.get();
    return (double) (current - minId) / step;
  }
}
