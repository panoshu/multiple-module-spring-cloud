package com.example.shared.event.dispatcher;

import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RocketMQEventDispatcher 5.x 线格式")
class RocketMQEventDispatcherTest {

  private static final String TOPIC = "event";

  @Test
  @DisplayName("补偿流 integrationEvent 为空 domainEvent 时使用 fallback key")
  void should_handle_null_domain_event_in_recovery_flow() {
    Producer producer = mock(Producer.class);
    when(producer.sendAsync(any())).thenReturn(
      CompletableFuture.completedFuture(mock(SendReceipt.class)));
    RocketMQEventDispatcher dispatcher = new RocketMQEventDispatcher(
      ClientServiceProvider.loadService(), producer, TOPIC);

    assertThatCode(() -> dispatcher.dispatch(null, Map.of("data", "test")))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("发送显式类型 + JSON 载荷信封，tag 为事件简单类型名")
  void sendsEnvelopeWithExplicitType() {
    Producer producer = mock(Producer.class);
    when(producer.sendAsync(any())).thenReturn(
      CompletableFuture.completedFuture(mock(SendReceipt.class)));
    RocketMQEventDispatcher dispatcher = new RocketMQEventDispatcher(
      ClientServiceProvider.loadService(), producer, TOPIC);
    TestEvent event = new TestEvent("E-1");

    dispatcher.dispatch(null, event);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer).sendAsync(captor.capture());
    Message message = captor.getValue();
    String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
    assertThat(message.getTopic()).isEqualTo(TOPIC);
    assertThat(message.getTag()).contains("TestEvent");
    assertThat(body).contains(TestEvent.class.getName()).contains("payloadJson").contains("E-1");
  }

  public record TestEvent(String id) {
  }
}
