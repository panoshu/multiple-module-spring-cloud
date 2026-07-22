package com.example.core.application.engine.listener;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.core.application.engine.service.FlowOrchestrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

/**
 * ApprovalResultEventListener 单元测试
 * <p>
 * 验证审批结果集成事件监听器的核心行为：
 * <ul>
 * <li>Approved 事件调用 advanceByApprovalResult(businessNo, "APPROVED")</li>
 * <li>Rejected 事件调用 advanceByApprovalResult(businessNo, "REJECTED")</li>
 * <li>Withdrawn 事件调用 advanceByApprovalResult(businessNo, "WITHDRAWN")</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@DisplayName("ApprovalResultEventListener 审批结果事件监听器测试")
@ExtendWith(MockitoExtension.class)
class ApprovalResultEventListenerTest {

  private static final String EVENT_ID = "evt-001";
  private static final String INSTANCE_ID = "inst-001";
  private static final String BUSINESS_NO = "app-001";
  private static final String BUSINESS_TYPE = "ACC_PLAN_CREATE";
  private static final LocalDateTime OCCURRED_ON = LocalDateTime.now();

  @Mock
  private FlowOrchestrationService orchestrationService;

  @InjectMocks
  private ApprovalResultEventListener listener;

  @Test
  @DisplayName("审批通过事件应调用 advanceByApprovalResult 传入 APPROVED")
  void handleApproved_shouldAdvanceWithApprovedResult() {
    // given: 审批通过事件
    ApprovalInstanceApprovedEventDTO event = new ApprovalInstanceApprovedEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.handleApproved(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "APPROVED");
  }

  @Test
  @DisplayName("审批驳回事件应调用 advanceByApprovalResult 传入 REJECTED")
  void handleRejected_shouldAdvanceWithRejectedResult() {
    // given: 审批驳回事件
    ApprovalInstanceRejectedEventDTO event = new ApprovalInstanceRejectedEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.handleRejected(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "REJECTED");
  }

  @Test
  @DisplayName("审批撤回事件应调用 advanceByApprovalResult 传入 WITHDRAWN")
  void handleWithdrawn_shouldAdvanceWithWithdrawnResult() {
    // given: 审批撤回事件
    ApprovalInstanceWithdrawnEventDTO event = new ApprovalInstanceWithdrawnEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.handleWithdrawn(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "WITHDRAWN");
  }

  @Test
  @DisplayName("Spring @EventListener 入口 onApproved 应委托给 handleApproved")
  void onApproved_shouldDelegateToHandleApproved() {
    // given
    ApprovalInstanceApprovedEventDTO event = new ApprovalInstanceApprovedEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.onApproved(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "APPROVED");
  }

  @Test
  @DisplayName("Spring @EventListener 入口 onRejected 应委托给 handleRejected")
  void onRejected_shouldDelegateToHandleRejected() {
    // given
    ApprovalInstanceRejectedEventDTO event = new ApprovalInstanceRejectedEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.onRejected(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "REJECTED");
  }

  @Test
  @DisplayName("Spring @EventListener 入口 onWithdrawn 应委托给 handleWithdrawn")
  void onWithdrawn_shouldDelegateToHandleWithdrawn() {
    // given
    ApprovalInstanceWithdrawnEventDTO event = new ApprovalInstanceWithdrawnEventDTO(
      EVENT_ID, INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE, OCCURRED_ON
    );

    // when
    listener.onWithdrawn(event);

    // then
    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "WITHDRAWN");
  }
}
