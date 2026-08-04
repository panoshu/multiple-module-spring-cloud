package com.example.shared.domain.event;

/**
 * 集成事件转换器 SPI。
 * <p>
 * 领域事件留在各业务的 domain 层，跨服务通信需要转换为不依赖领域对象的"集成事件"（纯 POJO）。
 * 各业务模块在 infrastructure 层实现此接口，注册为 Spring Bean。
 *
 * @param <D> 领域事件类型
 */
public interface IntegrationEventConverter<D extends DomainEvent> {

  /**
   * 此转换器支持的领域事件类型
   */
  Class<D> supportedEventType();

  /**
   * 将领域事件转换为集成事件（纯 POJO，可跨服务序列化）
   */
  Object toIntegrationEvent(D domainEvent);

  /**
   * 集成事件类型标识，默认使用领域事件类名。用于 MQ topic 路由和落库标识。
   */
  default String integrationEventType() {
    return supportedEventType().getSimpleName();
  }
}
