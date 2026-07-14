package com.example.shared.event.deliverer;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 事件投递器 (Infrastructure Service)
 * 职责：负责将单个事件“安全地”投递到指定通道，并处理状态流转 (Processing -> Success/Fail)。
 * 该组件被 EventBus (实时流) 和 RecoveryJob (补偿流) 共享。
 */
@Slf4j
@RequiredArgsConstructor
public class EventDeliverer {

  private final EventStore eventStore;

  /**
   * 执行投递
   *
   * @param dispatcher 目标分发器
   * @param event      事件本体
   * @param logId      分发日志ID (必须预先存在)
   */
  public void deliver(EventDispatcher dispatcher, DomainEvent event, long logId) {
    String channel = dispatcher.getChannelName();
    String eventId = event.eventId().toString();

    try {
      // 1. [双重检查]
      // 如果是补偿任务调用，可能存在并发。
      // 可以在这里简单check一下状态，或者依赖数据库乐观锁/update行锁

      // 2. 执行实际发送
      log.debug("Delivering event {} to channel {}", eventId, channel);
      dispatcher.dispatch(event);

      // 3. 标记成功 (独立事务)
      eventStore.markSuccess(logId);

    } catch (Exception e) {
      log.error("Delivery failed. Channel: {}, Event: {}, LogId: {}", channel, eventId, logId, e);

      // 4. 标记失败 (独立事务)
      // 包含记录异常信息、增加重试计数、计算下次重试时间
      try {
        eventStore.markFailure(logId, e);
      } catch (Exception ex) {
        log.error("Failed to mark failure for logId: {}", logId, ex);
      }
    }
  }
}
