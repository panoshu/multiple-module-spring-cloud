package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 出站文件生成完成集成事件监听器。
 * <p>
 * 消费 {@link OutboundFileGeneratedEventDTO}（由 file-service 出站文件生成完成后发布），
 * 根据关联的 {@code fileTaskId} 推进业务申请单流程。
 * <p>
 * <b>【关联机制】</b>出站请求中的 {@code fileTaskId} 等于 {@code applicationId}，
 * 因此可直接加载对应申请单并推进步骤。
 * <p>
 * <b>【演示环境】</b>通过 Spring {@link EventListener} 接收本地 {@code ApplicationEventPublisher}
 * 发布的事件；<b>【生产环境】</b>由 RocketMQ 消费者直接调用 {@link #handleOutboundFileGenerated(OutboundFileGeneratedEventDTO)}。
 *
 * @author core-kernel
 * @since 2026/10/29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundFileEventListener {

  private final FlowOrchestrationService orchestrationService;

  /**
   * Spring 事件入口：接收 {@link OutboundFileGeneratedEventDTO} 并委托给处理方法。
   *
   * @param event 出站文件生成完成事件 DTO
   */
  @EventListener
  public void onOutboundFileGenerated(OutboundFileGeneratedEventDTO event) {
    handleOutboundFileGenerated(event);
  }

  /**
   * 核心处理逻辑（Spring 和 RocketMQ 共用入口）。
   * <p>
   * 通过 {@code fileTaskId}(= applicationId) 定位申请单并推进至下一步。
   *
   * @param event 出站文件生成完成事件 DTO
   */
  public void handleOutboundFileGenerated(OutboundFileGeneratedEventDTO event) {
    log.info("收到出站文件生成完成事件, fileTaskId: {}, targetTemplateId: {}, fileCount: {}",
      event.fileTaskId(), event.targetTemplateId(),
      event.generatedFiles() != null ? event.generatedFiles().size() : 0);

    orchestrationService.advanceByOutboundFile(event.fileTaskId());
  }
}