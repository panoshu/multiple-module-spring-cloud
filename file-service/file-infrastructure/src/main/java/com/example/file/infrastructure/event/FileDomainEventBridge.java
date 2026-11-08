package com.example.file.infrastructure.event;

import com.example.file.domain.event.FileParsedEvent;
import com.example.shared.domain.event.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 领域事件桥接器：本地 Spring 事件 → shared-event-starter 总线（2026-11-08 批次1 接线）.
 *
 * <p>链路：ParseTask 终态注册事件 → Repository.save 时发本地 Spring 事件 →
 * 本桥接器 → {@link EventBus}（event-starter）→ IntegrationEventConverter 转
 * FileParsedEventDTO → eventStore 事务内持久化 → dispatcher（本地/Redis/RocketMQ）
 * 异步投递。至此 file 的解析结果具备了跨服务通知能力.</p>
 *
 * <p>同 JVM 消费方仍可直接 @EventListener 本地事件（Spring 同步分发不受影响）；
 * 桥接只增加持久化与远程分发，不改变本地语义.</p>
 *
 * @author file-service
 */
@Slf4j
@Component
public class FileDomainEventBridge {

  private final EventBus eventBus;

  public FileDomainEventBridge(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  @EventListener
  public void onFileParsed(FileParsedEvent event) {
    log.info("桥接 FileParsedEvent → event-starter 总线: fileTaskId={}, status={}, subTasks={}",
      event.fileTaskId().value(), event.status(), event.totalSubTasks());
    eventBus.publish(event);
  }
}
