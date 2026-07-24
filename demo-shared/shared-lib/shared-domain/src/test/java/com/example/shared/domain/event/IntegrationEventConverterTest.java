package com.example.shared.domain.event;

import com.example.shared.primitives.identity.EventId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationEventConverterTest {

    @Test
    void should_return_supported_event_type() {
        IntegrationEventConverter<TestEvent> converter = new TestEventConverter();
        assertThat(converter.supportedEventType()).isEqualTo(TestEvent.class);
    }

    @Test
    void should_return_integration_event_type_as_simple_name_by_default() {
        IntegrationEventConverter<TestEvent> converter = new TestEventConverter();
        assertThat(converter.integrationEventType()).isEqualTo("TestEvent");
    }

    @Test
    void should_convert_domain_event_to_integration_event() {
        IntegrationEventConverter<TestEvent> converter = new TestEventConverter();
        TestEvent event = new TestEvent(EventId.generate(), LocalDateTime.now(), "data");
        String integration = (String) converter.toIntegrationEvent(event);
        assertThat(integration).isEqualTo("data");
    }

    record TestEvent(EventId eventId, LocalDateTime occurredOn, String data) implements DomainEvent {}

    static class TestEventConverter implements IntegrationEventConverter<TestEvent> {
        @Override
        public Class<TestEvent> supportedEventType() { return TestEvent.class; }
        @Override
        public Object toIntegrationEvent(TestEvent event) { return event.data(); }
    }
}
