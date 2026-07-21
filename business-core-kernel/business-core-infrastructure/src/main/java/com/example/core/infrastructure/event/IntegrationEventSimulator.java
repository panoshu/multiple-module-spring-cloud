package com.example.core.infrastructure.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 集成事件模拟器（演示环境用）
 * <p>
 * 生产环境由 RocketMQ 消费者替代，直接调用监听器的处理方法。
 * 本组件将集成事件 DTO 通过 Spring {@link ApplicationEventPublisher} 发布到本地上下文，
 * 供 {@code business-core-application} 中的 {@code @EventListener} 监听器消费，
 * 从而在演示环境模拟跨服务的事件传递。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventSimulator {

  private final ApplicationEventPublisher publisher;

  /**
   * 发布集成事件 DTO 到本地 Spring 上下文。
   *
   * @param integrationEventDTO 集成事件 DTO（如 FileParsedEventDTO、ApprovalInstanceApprovedEventDTO 等）
   */
  public void publish(Object integrationEventDTO) {
    publisher.publishEvent(integrationEventDTO);
  }
}
