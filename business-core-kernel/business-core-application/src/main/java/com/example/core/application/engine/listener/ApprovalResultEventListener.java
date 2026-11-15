package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.inbound.ApprovalResultMessage;
import com.example.core.application.engine.inbound.ApprovalResultMessage.ApprovalResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果入站消息监听器。
 * <p>
 * 消费 adapter 层转换后的 {@link ApprovalResultMessage}：审批通过推进业务流程，
 * 驳回/撤回保持终止语义。应用层不依赖 approval-service 的事件 DTO 类型。
 * <p>
 * <b>【演示环境】</b>通过 Spring {@link EventListener} 接收本地 {@code ApplicationEventPublisher}
 * 发布的消息；<b>【生产环境】</b>由 MQ 消费者转换为同一消息后调用 {@link #handleApprovalResult}。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalResultEventListener {

  private static final String CONSUMER = "ApprovalResultEventListener";

  private final FlowOrchestrationService orchestrationService;
  private final EventReceiptGateway eventReceiptGateway;
  private final CallbackCorrelationService callbackCorrelationService;

  /**
   * Spring 消息入口：接收统一后的审批结果消息。
   *
   * @param message 审批结果入站消息
   */
  @EventListener
  public void onApprovalResult(ApprovalResultMessage message) {
    handleApprovalResult(message);
  }

  /**
   * 处理审批结果：按 requestNo 关联外部协作并推进流程。
   *
   * @param message 审批结果入站消息
   */
  public void handleApprovalResult(ApprovalResultMessage message) {
    if (!eventReceiptGateway.tryConsume(message.eventId(), CONSUMER)) {
      log.info("重复审批结果事件已忽略, eventId: {}", message.eventId());
      return;
    }
    log.info("收到审批结果: result={}, instanceId={}, businessNo={}, requestNo={}",
      message.result(), message.instanceId(), message.businessNo(), message.requestNo());

    CallbackOutcome outcome = switch (message.result()) {
      case APPROVED -> CallbackOutcome.SUCCEEDED;
      case REJECTED -> CallbackOutcome.REJECTED;
      case WITHDRAWN -> CallbackOutcome.WITHDRAWN;
    };
    CorrelationResult correlation = callbackCorrelationService.correlate(new CallbackCommand(
      message.requestNo(), message.instanceId(), InteractionType.APPROVAL,
      outcome, message.instanceId(), "SYSTEM"));

    switch (correlation.classification()) {
      case APPLIED -> {
        if (message.result() == ApprovalResult.APPROVED) {
          // 审批通过：完成等待步骤（不重跑 Handler 管线）
          orchestrationService.completeWaitingStep(correlation.applicationId());
        } else {
          orchestrationService.advanceByApprovalResult(
            correlation.applicationId().value(), message.result().name());
        }
      }
      case NO_CORRELATION -> orchestrationService.advanceByApprovalResult(
        message.businessNo(), message.result().name());
      case UNMATCHED, OUT_OF_ORDER -> log.warn(
        "审批结果回调未推进流程, classification={}, requestNo={}, instanceId={}, eventId={}",
        correlation.classification(), message.requestNo(), message.instanceId(), message.eventId());
    }
  }
}
