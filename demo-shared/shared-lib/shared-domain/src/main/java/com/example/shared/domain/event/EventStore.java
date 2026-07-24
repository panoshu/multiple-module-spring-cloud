package com.example.shared.domain.event;

import java.util.List;

public interface EventStore {
  // 1. 保存原始事件 + 集成事件（双 payload）
  //    integrationEvent 为 null 表示无转换器，降级为发送领域事件
  void save(DomainEvent event, Object integrationEvent, String integrationType);

  // 2. [同步] 初始化分发日志 (状态=PENDING)
  long initDispatchLog(String eventId, String channel);

  // 3. 标记成功
  void markSuccess(long logId);

  // 4. 标记失败
  void markFailure(long logId, Throwable ex);

  // 5. 查找待补偿的日志（用 integration_payload 反序列化为 Map，避免 Class.forName）
  List<PendingEntry> findPendingLogs(int batchSize);

  // 数据传输对象
  record PendingEntry(
      long logId,
      Object integrationEvent,
      String channel,
      String integrationType,
      int retryCount
  ) {}
}
