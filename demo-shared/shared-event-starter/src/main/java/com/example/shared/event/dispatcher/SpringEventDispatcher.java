package com.example.shared.event.dispatcher;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
public class SpringEventDispatcher implements EventDispatcher {
  private final ApplicationEventPublisher publisher;

  @Override
  public void dispatch(DomainEvent event) {
    publisher.publishEvent(event);
  }

  @Override
  public String getChannelName() {
    return "spring-local";
  }

  @Override
  public boolean isRemote() {
    return false; // 本地事件不强制异步，交给 TransactionalEventListener 处理
  }
}
