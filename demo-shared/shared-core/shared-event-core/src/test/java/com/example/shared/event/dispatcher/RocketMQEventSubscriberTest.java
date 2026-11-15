package com.example.shared.event.dispatcher;

import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RocketMQEventSubscriber 本地事件桥接")
class RocketMQEventSubscriberTest {

  @Test
  @DisplayName("按信封类型还原 DTO 并发布本地 Spring 事件")
  void publishesTypedEvent() throws Exception {
    String payload = "{\"eventType\":\"%s\",\"payloadJson\":\"{\\\"id\\\":\\\"E-1\\\"}\"}"
      .formatted(RocketMQEventSubscriberTest.Payload.class.getName());
    MessageView message = mock(MessageView.class);
    when(message.getBody()).thenReturn(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)));
    when(message.getMessageId()).thenReturn(mock(MessageId.class));
    SimpleConsumer consumer = mock(SimpleConsumer.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    RocketMQEventSubscriber subscriber = new RocketMQEventSubscriber(consumer, publisher);
    subscriber.handle(message);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isEqualTo(new Payload("E-1"));
  }

  public record Payload(String id) {
  }
}
