package com.example.shared.event.contract;

import com.example.shared.domain.event.DomainEvent;

/**
 * 集成事件转换器 SPI。
 * <p>
 * 领域事件留在各业务的 domain 层，跨服务通信需要转换为不依赖领域对象的"集成事件"（纯 POJO）。
 * 各业务模块在 infrastructure 层实现此接口，注册为 Spring Bean。
 *
 * @param <D> 领域事件类型
 */
public interface IntegrationEventConverter<D extends DomainEvent> {

  Class<D> supportedEventType();

  Object toIntegrationEvent(D domainEvent);

  default String integrationEventType() {
    return supportedEventType().getSimpleName();
  }
}
