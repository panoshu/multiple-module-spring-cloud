package com.example.shared.event.bus;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.domain.event.IntegrationEventConverter;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.identifier.id.EventId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultEventBusTest {

  private EventStore eventStore;
  private EventDeliverer deliverer;
  private EventDispatcher remoteDispatcher;
  private EventDispatcher localDispatcher;
  private IntegrationEventConverter<TestEvent> converter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    eventStore = mock(EventStore.class);
    deliverer = mock(EventDeliverer.class);
    remoteDispatcher = mock(EventDispatcher.class);
    when(remoteDispatcher.isRemote()).thenReturn(true);
    when(remoteDispatcher.getChannelName()).thenReturn("rocketmq");
    localDispatcher = mock(EventDispatcher.class);
    when(localDispatcher.isRemote()).thenReturn(false);
    when(localDispatcher.getChannelName()).thenReturn("spring-local");
    converter = mock(IntegrationEventConverter.class);
    when(converter.supportedEventType()).thenReturn(TestEvent.class);
    when(converter.integrationEventType()).thenReturn("TestEvent");
    when(converter.toIntegrationEvent(any())).thenReturn(Map.of("data", "hello"));
    when(eventStore.initDispatchLog(anyString(), anyString())).thenReturn(1L);
  }

  @Test
  void should_convert_and_save_dual_payload_then_dispatch() {
    DefaultEventBus bus = new DefaultEventBus(List.of(remoteDispatcher, localDispatcher), eventStore, deliverer, List.of(converter));
    TestEvent event = new TestEvent(EventId.generate(), LocalDateTime.now(), "hello");

    bus.publish(event);

    verify(converter).toIntegrationEvent(event);
    verify(eventStore).save(eq(event), any(), eq("TestEvent"));
    verify(eventStore).initDispatchLog(event.eventId().toString(), "rocketmq");
    verify(localDispatcher).dispatch(eq(event), any());
  }

  @Test
  void should_fallback_to_domain_event_when_no_converter() {
    DefaultEventBus bus = new DefaultEventBus(List.of(localDispatcher), eventStore, deliverer, List.of());
    TestEvent event = new TestEvent(EventId.generate(), LocalDateTime.now(), "hello");

    bus.publish(event);

    // 无转换器时，integrationEvent 为 null
    verify(eventStore).save(eq(event), isNull(), isNull());
    verify(localDispatcher).dispatch(eq(event), isNull());
  }

  record TestEvent(EventId eventId, LocalDateTime occurredOn, String data) implements DomainEvent {
  }
}
