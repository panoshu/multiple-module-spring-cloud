package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public class RedisEventDispatcher implements EventDispatcher {
  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
    Object payload = integrationEvent != null ? integrationEvent : domainEvent;
    String integrationType = payload.getClass().getSimpleName();
    String topic = "event.%s".formatted(integrationType);
    redisTemplate.convertAndSend(topic, payload);
  }

  @Override
  public String getChannelName() {
    return "redis-pubsub";
  }
}
