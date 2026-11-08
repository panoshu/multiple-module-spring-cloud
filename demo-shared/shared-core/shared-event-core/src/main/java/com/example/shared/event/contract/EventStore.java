package com.example.shared.event.contract;

import com.example.shared.domain.event.DomainEvent;

import java.util.List;

/**
 * 事件存储端口（持久化 + 投递日志管理）.
 *
 * @author shared-event-core
 */
public interface EventStore {

  void save(DomainEvent event, Object integrationEvent, String integrationType);

  long initDispatchLog(String eventId, String channel);

  void markSuccess(long logId);

  void markFailure(long logId, Throwable ex);

  /**
   * 认领一批待恢复投递的事件（2026-11-06 去分布式锁重构）.
   *
   * <p>认领语义（多实例并发安全）：行级互斥（FOR UPDATE SKIP LOCKED）+
   * 状态置 PROCESSING——多实例同时扫描各自认领互不重叠的行，天然并行、
   * 崩溃自动由 PROCESSING 超时回收兜底。替代原"分布式锁保护整个扫描任务"
   * 的粗粒度互斥（互斥资源在 DB，就该用 DB 的行锁——不引入第三个系统）.</p>
   *
   * <p>调用方约定：认领后必须回写结果（markSuccess / markFailure）；
   * 处理中崩溃的行停留在 PROCESSING，超过回收时限（默认 5 分钟）后
   * 可被重新认领.</p>
   *
   * @param batchSize 单轮认领上限
   */
  List<PendingEntry> claimPendingLogs(int batchSize);

  record PendingEntry(
    long logId,
    Object integrationEvent,
    String channel,
    String integrationType,
    int retryCount
  ) {
  }
}
