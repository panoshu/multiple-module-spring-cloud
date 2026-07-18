package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

/**
 * RocketMQ 事件分发器
 * 职责：将领域事件发送到 RocketMQ
 *
 * Topic 策略：使用事件类型作为 Topic 名称
 * Tag 策略：使用事件类型作为 Tag
 * 发送模式：异步发送
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMQEventDispatcher implements EventDispatcher {

  private final RocketMQTemplate rocketMQTemplate;

  @Override
  public void dispatch(DomainEvent event) {
    // Topic 策略：使用事件类型作为 Topic
    String topic = "event_%s".formatted(event.eventType());
    String key = event.eventId().toString();

    try {
      // RocketMQ 发送消息
      // destination 格式：topic:tag
      String destination = topic + ":" + event.eventType();

      rocketMQTemplate.asyncSend(destination, event, new SendCallback() {
        @Override
        public void onSuccess(SendResult sendResult) {
          log.debug("Event sent successfully. Topic: {}, EventId: {}, MsgId: {}",
                    topic, key, sendResult.getMsgId());
        }

        @Override
        public void onException(Throwable e) {
          log.error("Failed to send event. Topic: {}, EventId: {}", topic, key, e);
          throw new RuntimeException("RocketMQ send failed", e);
        }
      });

    } catch (Exception e) {
      log.error("Exception during RocketMQ dispatch. EventId: {}", key, e);
      throw new RuntimeException("RocketMQ dispatch exception", e);
    }
  }

  @Override
  public String getChannelName() {
    return "rocketmq";
  }
}