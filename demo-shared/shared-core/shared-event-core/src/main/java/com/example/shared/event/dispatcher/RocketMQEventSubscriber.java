package com.example.shared.event.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * RocketMQ 5.x 事件订阅器（X-04）。
 *
 * <p>每个服务使用独立 consumer group（默认 {@code <spring.application.name>-event}），
 * 订阅统一 topic 并接收全部 tag；收到消息后按 {@link RocketMQEventEnvelope} 的
 * 全限定类型名还原集成事件 DTO，再发布为本地 Spring 事件，复用现有 adapter/
 * application 监听器，不改变业务消费代码。</p>
 *
 * <p>类型名白名单限定为 {@code com.example.}，防止消息载荷触发任意类加载。</p>
 *
 * @author shared-event-core
 */
@Slf4j
public class RocketMQEventSubscriber implements AutoCloseable {

  private static final String ALLOWED_TYPE_PREFIX = "com.example.";
  private static final int BATCH_SIZE = 32;

  private final SimpleConsumer consumer;
  private final ApplicationEventPublisher publisher;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private volatile boolean running = true;
  private Thread worker;

  public RocketMQEventSubscriber(SimpleConsumer consumer, ApplicationEventPublisher publisher) {
    this.consumer = consumer;
    this.publisher = publisher;
  }

  public void start() {
    worker = Thread.ofVirtual().name("rocketmq-event-subscriber").start(this::loop);
    log.info("RocketMQ 事件订阅器已启动: group={}", consumer.getConsumerGroup());
  }

  private void loop() {
    while (running) {
      try {
        List<MessageView> messages = consumer.receive(BATCH_SIZE, Duration.ofSeconds(20));
        for (MessageView message : messages) {
          try {
            handle(message);
            consumer.ack(message);
          } catch (Exception e) {
            log.warn("RocketMQ 事件处理失败，稍后重投: msgId={}", message.getMessageId(), e);
            // 不 ack：由服务端不可见时长到期后重投
          }
        }
      } catch (Exception e) {
        if (!running) {
          return;
        }
        log.warn("RocketMQ 拉取事件失败，2s 后重试", e);
        sleepQuietly();
      }
    }
  }

  void handle(MessageView message) throws Exception {
    String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
    RocketMQEventEnvelope envelope = objectMapper.readValue(body, RocketMQEventEnvelope.class);
    String eventType = envelope.eventType();
    if (eventType == null || !eventType.startsWith(ALLOWED_TYPE_PREFIX)) {
      log.warn("忽略非白名单事件类型: {}", eventType);
      return;
    }
    Class<?> type;
    try {
      type = Class.forName(eventType);
    } catch (ClassNotFoundException e) {
      // 本服务未引入该契约模块：忽略是正常行为
      log.debug("本服务无事件类型 {}，忽略消息", eventType);
      return;
    }
    Object event = objectMapper.readValue(envelope.payloadJson(), type);
    publisher.publishEvent(event);
    log.debug("RocketMQ 事件已发布到本地: type={}, msgId={}", eventType, message.getMessageId());
  }

  private void sleepQuietly() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    running = false;
    if (worker != null) {
      worker.interrupt();
    }
    try {
      consumer.close();
    } catch (Exception e) {
      log.warn("关闭 RocketMQ SimpleConsumer 失败", e);
    }
  }
}
