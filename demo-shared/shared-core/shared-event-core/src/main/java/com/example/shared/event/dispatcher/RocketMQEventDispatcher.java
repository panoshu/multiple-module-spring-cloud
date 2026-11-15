package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.event.contract.EventDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;

/**
 * RocketMQ 5.x 事件分发器（X-04）。
 *
 * <p>使用官方 gRPC 客户端（`rocketmq-client-java`）直连 RocketMQ Proxy 的 8081 端点，
 * 不再依赖 4.x remoting 的 NameServer 协议。消息统一发送到配置的 topic，
 * tag 为集成事件简单类型名，key 为领域事件 eventId（便于按 eventId 查询/去重）。</p>
 *
 * <p>线路格式见 {@link RocketMQEventEnvelope}：显式携带事件 DTO 全限定名 + JSON 载荷，
 * 消费端不需要 Jackson 多态类型信息。</p>
 *
 * @author shared-event-core
 */
@Slf4j
public class RocketMQEventDispatcher implements EventDispatcher {

  private final ClientServiceProvider provider;
  private final Producer producer;
  private final String topic;
  private final ObjectMapper objectMapper;

  public RocketMQEventDispatcher(ClientServiceProvider provider, Producer producer, String topic) {
    this.provider = provider;
    this.producer = producer;
    this.topic = topic;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Override
  public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
    Object payload = integrationEvent != null ? integrationEvent : domainEvent;
    String eventType = payload.getClass().getName();
    String key = domainEvent != null
      ? domainEvent.eventId().value()
      : "recovery-" + payload.getClass().getSimpleName();
    try {
      String envelope = objectMapper.writeValueAsString(
        new RocketMQEventEnvelope(eventType, objectMapper.writeValueAsString(payload)));
      Message message = provider.newMessageBuilder()
        .setTopic(topic)
        .setTag(payload.getClass().getSimpleName())
        .setKeys(key)
        .setBody(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .build();
      producer.sendAsync(message).whenComplete((receipt, error) -> {
        if (error != null) {
          log.error("RocketMQ send failed for event {}", key, error);
        } else {
          log.debug("RocketMQ send success: topic={}, msgId={}",
            topic, receipt == null ? null : receipt.getMessageId());
        }
      });
    } catch (Exception e) {
      log.error("RocketMQ 事件序列化/发送失败: eventType={}, key={}", eventType, key, e);
    }
  }

  @Override
  public String getChannelName() {
    return "rocketmq";
  }
}
