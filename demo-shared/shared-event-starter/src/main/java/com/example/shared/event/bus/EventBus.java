package com.example.shared.event.bus;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.utils.concurrent.VirtualThreadExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class EventBus implements com.example.shared.domain.event.EventBus {

  private final List<EventDispatcher> dispatchers;
  private final EventStore eventStore;
  private final EventDeliverer eventDeliverer; // 注入投递器

  @Override
  public void publish(DomainEvent event) {
    // 1. [同步] 落库业务事件
    try {
      eventStore.save(event);
    } catch (Exception e) {
      log.error("EventBus: Failed to save event. EventId: {}", event.eventId(), e);
      throw e; // 阻断业务
    }

    // 2. 遍历分发
    for (EventDispatcher dispatcher : dispatchers) {
      if (dispatcher.isRemote()) {
        // 3a. [远程] 初始化日志 -> 注册回调 -> 异步委托
        long logId = eventStore.initDispatchLog(event.eventId().toString(), dispatcher.getChannelName());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              // 事务提交后，委托给 Deliverer 去跑
              VirtualThreadExecutor.executeAsync(() ->
                eventDeliverer.deliver(dispatcher, event, logId)
              );
            }
          });
        } else {
          VirtualThreadExecutor.executeAsync(() ->
            eventDeliverer.deliver(dispatcher, event, logId)
          );
        }
      } else {
        // 3b. [本地] 依然保持同步调用 (或根据策略调整)
        safeLocalDispatch(dispatcher, event);
      }
    }
  }

  private void safeLocalDispatch(EventDispatcher dispatcher, DomainEvent event) {
    try {
      dispatcher.dispatch(event);
    } catch (Exception e) {
      log.error("Local dispatch error", e);
    }
  }
}
