package com.example.shared.domain.event;

import java.util.List;

public interface EventStore {
  // 1. 保存原始事件
  void save(DomainEvent event);

  // 2. [同步] 初始化分发日志 (状态=PENDING)
  // 返回生成的日志ID
  long initDispatchLog(String eventId, String channel);

  // 3. 标记成功
  void markSuccess(long logId);

  // 4. 标记失败 (累加重试次数，设置下次重试时间)
  void markFailure(long logId, Throwable ex);

  // 5. 查找待补偿的日志 (JOIN event_store 以获取 payload)
  List<PendingEntry> findPendingLogs(int batchSize);

  // 数据传输对象
  record PendingEntry(long logId, DomainEvent event, String channel, int retryCount) {
  }
}
