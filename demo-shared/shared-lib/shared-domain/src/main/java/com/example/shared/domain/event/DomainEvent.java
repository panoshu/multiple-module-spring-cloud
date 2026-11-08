package com.example.shared.domain.event;

import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * 领域事件的通用接口。定义所有领域事件必须具备的基本属性。
 * 此接口本身不进行密封，允许在各个领域中定义具体的密封事件层次结构。
 *
 * @author <a href="mailto: me@panoshu.top">panoshu</a>
 * @version 1.0
 * @since 2025/6/1 13:50
 **/
public interface DomainEvent {
  EventId eventId();

  LocalDateTime occurredOn();

  default String eventType() {
    return this.getClass().getSimpleName();
  }
}
