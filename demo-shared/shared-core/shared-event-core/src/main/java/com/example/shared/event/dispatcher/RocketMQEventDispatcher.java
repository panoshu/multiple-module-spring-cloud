package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
@RequiredArgsConstructor
public class RocketMQEventDispatcher implements EventDispatcher {

  private final RocketMQTemplate rocketMQTemplate;

  @Override
  public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
    Object payload = integrationEvent != null ? integrationEvent : domainEvent;
    String integrationType = payload.getClass().getSimpleName();
    // topic: event_FileParsed, tag: FileParsed
    String destination = "event_%s:%s".formatted(integrationType, integrationType);
    // 补偿流(domainEvent=null)用 integrationType 作为 fallback key
    String key = domainEvent != null
        ? domainEvent.eventId().toString()
        : "recovery-" + integrationType;

    rocketMQTemplate.asyncSend(destination,
        MessageBuilder.withPayload(payload).setHeader("KEYS", key).build(),
        new org.apache.rocketmq.client.producer.SendCallback() {
          @Override
          public void onSuccess(org.apache.rocketmq.client.producer.SendResult result) {
            log.debug("RocketMQ send success: {}", result.getMsgId());
          }
          @Override
          public void onException(Throwable e) {
            log.error("RocketMQ send failed for event {}", key, e);
          }
        });
  }

  @Override
  public String getChannelName() { return "rocketmq"; }
}
