package com.example.core.application.listener;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.core.application.service.BusinessOrchestrationAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果集成事件监听器。
 * <p>
 * 消费 approval-service 发布的三类审批结果事件：
 * <ul>
 * <li>{@link ApprovalInstanceApprovedEventDTO} — 审批通过，推进业务流程；</li>
 * <li>{@link ApprovalInstanceRejectedEventDTO} — 审批驳回，终止业务流程；</li>
 * <li>{@link ApprovalInstanceWithdrawnEventDTO} — 审批撤回，终止业务流程。</li>
 * </ul>
 * <p>
 * <b>【演示环境】</b>通过 Spring {@link EventListener} 接收本地 {@code ApplicationEventPublisher}
 * 发布的事件；<b>【生产环境】</b>由 RocketMQ 消费者直接调用对应的 {@code handle*} 方法。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalResultEventListener {

  private static final String APPROVAL_RESULT_APPROVED = "APPROVED";
  private static final String APPROVAL_RESULT_REJECTED = "REJECTED";
  private static final String APPROVAL_RESULT_WITHDRAWN = "WITHDRAWN";

  private final BusinessOrchestrationAppService orchestrationService;

  /**
   * Spring 事件入口：接收审批通过事件。
   *
   * @param event 审批通过事件 DTO
   */
  @EventListener
  public void onApproved(ApprovalInstanceApprovedEventDTO event) {
    handleApproved(event);
  }

  /**
   * Spring 事件入口：接收审批驳回事件。
   *
   * @param event 审批驳回事件 DTO
   */
  @EventListener
  public void onRejected(ApprovalInstanceRejectedEventDTO event) {
    handleRejected(event);
  }

  /**
   * Spring 事件入口：接收审批撤回事件。
   *
   * @param event 审批撤回事件 DTO
   */
  @EventListener
  public void onWithdrawn(ApprovalInstanceWithdrawnEventDTO event) {
    handleWithdrawn(event);
  }

  /**
   * 处理审批通过事件：推进业务流程。
   *
   * @param event 审批通过事件 DTO
   */
  public void handleApproved(ApprovalInstanceApprovedEventDTO event) {
    log.info("审批通过, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
    orchestrationService.advanceByApprovalResult(event.businessNo(), APPROVAL_RESULT_APPROVED);
  }

  /**
   * 处理审批驳回事件：终止业务流程。
   *
   * @param event 审批驳回事件 DTO
   */
  public void handleRejected(ApprovalInstanceRejectedEventDTO event) {
    log.info("审批驳回, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
    orchestrationService.advanceByApprovalResult(event.businessNo(), APPROVAL_RESULT_REJECTED);
  }

  /**
   * 处理审批撤回事件：终止业务流程。
   *
   * @param event 审批撤回事件 DTO
   */
  public void handleWithdrawn(ApprovalInstanceWithdrawnEventDTO event) {
    log.info("审批撤回, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
    orchestrationService.advanceByApprovalResult(event.businessNo(), APPROVAL_RESULT_WITHDRAWN);
  }
}
