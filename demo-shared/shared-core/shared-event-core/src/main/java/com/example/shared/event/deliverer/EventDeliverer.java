package com.example.shared.event.deliverer;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EventDeliverer {

  private final EventStore eventStore;

  /**
   * 执行投递（实时流和补偿流共享）
   */
  public void deliver(EventDispatcher dispatcher, DomainEvent event,
                      Object integrationEvent, long logId) {
    String channel = dispatcher.getChannelName();
    String eventId = event.eventId().toString();
    try {
      log.debug("Delivering event {} to channel {}", eventId, channel);
      dispatcher.dispatch(event, integrationEvent);
      eventStore.markSuccess(logId);
    } catch (Exception e) {
      log.error("Delivery failed. Channel: {}, Event: {}, LogId: {}", channel, eventId, logId, e);
      try {
        eventStore.markFailure(logId, e);
      } catch (Exception ex) {
        log.error("Failed to mark failure for logId: {}", logId, ex);
      }
    }
  }

  /**
   * 补偿流重载：使用预先反序列化的 integrationEvent
   */
  public void deliverRecovered(EventDispatcher dispatcher, Object integrationEvent,
                               String integrationType, long logId) {
    String channel = dispatcher.getChannelName();
    try {
      log.debug("Recovering event logId={} type={} to channel {}", logId, integrationType, channel);
      dispatcher.dispatch(null, integrationEvent);
      eventStore.markSuccess(logId);
    } catch (Exception e) {
      log.error("Recovery delivery failed. Channel: {}, LogId: {}", channel, logId, e);
      try {
        eventStore.markFailure(logId, e);
      } catch (Exception ex) {
        log.error("Failed to mark failure for logId: {}", logId, ex);
      }
    }
  }
}
