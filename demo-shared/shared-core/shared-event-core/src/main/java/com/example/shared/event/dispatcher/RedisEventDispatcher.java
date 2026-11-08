package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.event.contract.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis Pub/Sub 事件分发器（发布端）.
 *
 * <p>2026-11-08 批次1 增强：载荷包装为 {@link EventEnvelope}（携带发布方
 * instanceId）——订阅端据此防回声（发布方本地已被 SpringEventDispatcher
 * 同步分发过一次）.</p>
 *
 * <p>通道命名：{@code event.{集成事件类型简名}}；订阅端按 pattern
 * {@code event.*} 订阅.</p>
 *
 * @author shared-event
 */
@RequiredArgsConstructor
public class RedisEventDispatcher implements EventDispatcher {

  private final RedisTemplate<String, Object> redisTemplate;
  private final String instanceId;

  @Override
  public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
    Object payload = integrationEvent != null ? integrationEvent : domainEvent;
    String integrationType = payload.getClass().getSimpleName();
    String topic = "event.%s".formatted(integrationType);
    redisTemplate.convertAndSend(topic, new EventEnvelope(instanceId, payload));
  }

  @Override
  public String getChannelName() {
    return "redis-pubsub";
  }
}
