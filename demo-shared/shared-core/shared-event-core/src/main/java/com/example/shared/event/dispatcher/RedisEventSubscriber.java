package com.example.shared.event.dispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Redis Pub/Sub 事件订阅端（2026-11-08 批次1：跨服务事件收发闭环）.
 *
 * <p>订阅 {@code event.*} 通道，把远端服务发布的集成事件转回本地
 * Spring 事件——本服务的 @EventListener 消费方（如 kernel 的
 * FileParsedEventListener）即可收到跨服务事件.</p>
 *
 * <p>防回声：信封的 sourceInstanceId 等于本实例时跳过（发布方本地已被
 * SpringEventDispatcher 同步分发）.</p>
 *
 * <p>宽容语义：反序列化失败（本服务 classpath 没有该 DTO 类型——
 * 未定义跨服务契约的事件）记 WARN 忽略，不影响其他消息.</p>
 *
 * @author shared-event
 */
@Slf4j
@RequiredArgsConstructor
public class RedisEventSubscriber implements MessageListener {

  private final RedisTemplate<String, Object> redisTemplate;
  private final ApplicationEventPublisher publisher;
  private final String instanceId;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      Object body = redisTemplate.getValueSerializer().deserialize(message.getBody());
      if (!(body instanceof EventEnvelope envelope)) {
        log.warn("[RedisEventSubscriber] 非 EventEnvelope 载荷，跳过: channel={}", new String(message.getChannel()));
        return;
      }
      if (envelope.from(instanceId)) {
        return; // 回声：自己发布的（本地已分发）
      }
      log.info("[RedisEventSubscriber] 跨服务事件到达: channel={}, type={}",
        new String(message.getChannel()),
        envelope.payload() != null ? envelope.payload().getClass().getSimpleName() : "null");
      publisher.publishEvent(envelope.payload());
    } catch (SerializationException | ClassCastException e) {
      log.warn("[RedisEventSubscriber] 载荷反序列化失败（本服务无该契约类型？），忽略: {}",
        e.getMessage());
    } catch (Exception e) {
      log.error("[RedisEventSubscriber] 消息处理异常", e);
    }
  }
}
