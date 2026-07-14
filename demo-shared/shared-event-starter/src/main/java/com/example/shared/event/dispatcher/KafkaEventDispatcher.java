package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaEventDispatcher implements EventDispatcher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public void dispatch(DomainEvent event) {
    // 生产环境建议：Topic = event.eventType() 或 统一 Topic
    String topic = "event.%s".formatted(event.eventType());
    String key = event.eventId().toString();
    // 这里的 send 是异步的，但 future 可能会抛出 SerializationException
    kafkaTemplate.send(topic, key, event)
      .whenComplete((result, ex) -> {
        if (ex != null) {
          throw new RuntimeException("Kafka send failed", ex);
        }
      });
  }

  @Override
  public String getChannelName() {
    return "kafka";
  }
}
