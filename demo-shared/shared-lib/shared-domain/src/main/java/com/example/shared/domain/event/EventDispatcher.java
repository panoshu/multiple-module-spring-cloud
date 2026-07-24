package com.example.shared.domain.event;

public interface EventDispatcher {
  /**
   * 分发事件
   * @param domainEvent 领域事件（本地分发使用）
   * @param integrationEvent 集成事件（远程分发使用，可能为 null 降级为领域事件）
   */
  void dispatch(DomainEvent domainEvent, Object integrationEvent);

  /**
   * 通道名称 (用于日志和审计)
   */
  String getChannelName();

  /**
   * 是否是远程通道 (SpringEvent 是本地，Redis/Kafka 是远程)
   * 远程通道需要等待事务提交后发送
   */
  default boolean isRemote() {
    return true;
  }
}
