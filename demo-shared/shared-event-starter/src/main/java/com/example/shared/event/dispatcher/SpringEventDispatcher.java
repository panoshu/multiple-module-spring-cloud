package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
public class SpringEventDispatcher implements EventDispatcher {
  private final ApplicationEventPublisher publisher;

  @Override
  public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
    // 本地分发始终发送领域事件，保留领域语义
    publisher.publishEvent(domainEvent);
  }

  @Override
  public String getChannelName() { return "spring-local"; }

  @Override
  public boolean isRemote() { return false; }
}
