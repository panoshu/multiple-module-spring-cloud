package com.example.shared.event.bus;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.domain.event.IntegrationEventConverter;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.utils.concurrent.VirtualThreadExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class EventBus implements com.example.shared.domain.event.EventBus {

  private final List<EventDispatcher> dispatchers;
  private final EventStore eventStore;
  private final EventDeliverer eventDeliverer;
  private final List<IntegrationEventConverter<?>> converters;

  private final Map<Class<?>, IntegrationEventConverter<?>> converterCache = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void publish(DomainEvent event) {
    // 1. 查找转换器
    IntegrationEventConverter<?> converter = findConverter(event);
    Object integrationEvent = converter != null
        ? ((IntegrationEventConverter) converter).toIntegrationEvent(event)
        : null;
    String integrationType = converter != null
        ? converter.integrationEventType()
        : null;

    // 2. 落库（领域事件 + 集成事件双份；无转换器时 integrationEvent 为 null）
    try {
      eventStore.save(event, integrationEvent, integrationType);
    } catch (Exception e) {
      log.error("EventBus: Failed to save event. EventId: {}", event.eventId(), e);
      throw e;
    }

    // 3. 遍历分发
    for (EventDispatcher dispatcher : dispatchers) {
      if (dispatcher.isRemote()) {
        long logId = eventStore.initDispatchLog(event.eventId().toString(), dispatcher.getChannelName());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              VirtualThreadExecutor.executeAsync(() ->
                  eventDeliverer.deliver(dispatcher, event, integrationEvent, logId));
            }
          });
        } else {
          VirtualThreadExecutor.executeAsync(() ->
              eventDeliverer.deliver(dispatcher, event, integrationEvent, logId));
        }
      } else {
        safeLocalDispatch(dispatcher, event, integrationEvent);
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private IntegrationEventConverter<?> findConverter(DomainEvent event) {
    return converterCache.computeIfAbsent(event.getClass(), clazz ->
        converters.stream()
            .filter(c -> c.supportedEventType() == clazz)
            .findFirst()
            .orElse(null)
    );
  }

  private void safeLocalDispatch(EventDispatcher dispatcher, DomainEvent event, Object integrationEvent) {
    try {
      dispatcher.dispatch(event, integrationEvent);
    } catch (Exception e) {
      log.error("Local dispatch error", e);
    }
  }
}
