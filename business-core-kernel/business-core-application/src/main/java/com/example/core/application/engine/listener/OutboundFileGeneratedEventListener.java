package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 出站文件生成事件监听器（2026-11-08 批次4 C3：导出闭环——kernel 内置件）.
 *
 * <p><b>链路</b>（设计决策 D1 三方分工）：OutboundGenerationHandler 触发生成
 * → file-service 生成完成发布 OutboundFileGeneratedEvent（Redis 事件通道）→
 * 本监听器按 fileTaskId 反查申请单推进流程；integration-service 监听同一
 * 事件做协议适配提交外部系统.</p>
 *
 * <p>事件 {@code @EventListener} 入口在 Redis 订阅端（RedisEventSubscriber）
 * 转发本地后触发；演示环境（无 Redis）下由 IntegrationEventSimulator 进程内直发.</p>
 *
 * @author business-core-kernel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundFileGeneratedEventListener {

  private static final String CONSUMER = "OutboundFileGeneratedEventListener";

  private final com.example.core.application.engine.service.FlowOrchestrationService orchestrationService;
  private final EventReceiptGateway eventReceiptGateway;
  private final CallbackCorrelationService callbackCorrelationService;

  @EventListener
  public void onOutboundFileGenerated(OutboundFileGeneratedEventDTO event) {
    if (!eventReceiptGateway.tryConsume(event.eventId(), CONSUMER)) {
      log.info("重复出站文件生成事件已忽略, eventId: {}", event.eventId());
      return;
    }
    log.info("收到出站文件生成事件, fileTaskId: {}, targetTemplateId: {}, files: {}",
      event.fileTaskId(), event.targetTemplateId(),
      event.generatedFiles() == null ? 0 : event.generatedFiles().size());
    try {
      CorrelationResult result = callbackCorrelationService.correlate(new CallbackCommand(
        event.requestNo(), event.fileTaskId(), InteractionType.FILE_OUTBOUND,
        CallbackOutcome.SUCCEEDED, event.fileTaskId(), "SYSTEM"));
      switch (result.classification()) {
        case APPLIED -> orchestrationService.completeWaitingStep(result.applicationId());
        case NO_CORRELATION -> orchestrationService.advanceByFileTaskId(event.fileTaskId());
        case UNMATCHED, OUT_OF_ORDER -> log.warn(
          "出站生成回调未推进流程, classification={}, requestNo={}, fileTaskId={}",
          result.classification(), event.requestNo(), event.fileTaskId());
      }
    } catch (Exception e) {
      // 推进失败仅记录（事件已到、文件已生成——失败留给人工/补偿；不吞真实事件语义
      log.error("出站生成后流程推进失败: fileTaskId={}", event.fileTaskId(), e);
    }
  }
}
