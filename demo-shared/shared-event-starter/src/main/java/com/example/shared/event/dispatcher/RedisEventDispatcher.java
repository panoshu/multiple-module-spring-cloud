package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/1/9 16:55
 */
@RequiredArgsConstructor
public class RedisEventDispatcher implements EventDispatcher {
  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public void dispatch(DomainEvent event) {
    // Topic 格式: event.UserRegistered
    String topic = "event.%s".formatted(event.eventType());
    redisTemplate.convertAndSend(topic, event);
  }

  @Override
  public String getChannelName() {
    return "redis-pubsub";
  }
}
