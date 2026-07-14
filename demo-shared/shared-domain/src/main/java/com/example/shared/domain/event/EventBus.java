package com.example.shared.domain.event;

/**
 * EventBus
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/2 23:57
 */
public interface EventBus {

  void publish(DomainEvent event);

}
