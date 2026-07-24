package com.example.shared.event.dispatcher;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class RocketMQEventDispatcherTest {

  @Test
  void should_handle_null_domain_event_in_recovery_flow() {
    RocketMQTemplate mockTemplate = mock(RocketMQTemplate.class);
    RocketMQEventDispatcher dispatcher = new RocketMQEventDispatcher(mockTemplate);

    Object integrationEvent = Map.of("data", "test");

    // 补偿流: deliverRecovered 调用 dispatch(null, integrationEvent)
    // 修复前: domainEvent.eventId().toString() 抛 NPE
    // 修复后: 用 integrationType 作为 fallback key
    assertDoesNotThrow(() -> dispatcher.dispatch(null, integrationEvent));
  }
}
