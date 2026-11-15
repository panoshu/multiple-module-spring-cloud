package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.inbound.ApprovalResultMessage;
import com.example.core.application.engine.inbound.ApprovalResultMessage.ApprovalResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.types.ApplicationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ApprovalResultEventListener 单元测试
 * <p>
 * 验证审批结果入站消息监听器的核心行为：
 * <ul>
 * <li>APPROVED 结果调用 advanceByApprovalResult(businessNo, "APPROVED")</li>
 * <li>REJECTED 结果调用 advanceByApprovalResult(businessNo, "REJECTED")</li>
 * <li>WITHDRAWN 结果调用 advanceByApprovalResult(businessNo, "WITHDRAWN")</li>
 * <li>重复 eventId 经消费回执短路，不重复推进</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DisplayName("ApprovalResultEventListener 审批结果消息监听器测试")
@ExtendWith(MockitoExtension.class)
class ApprovalResultEventListenerTest {

  private static final String EVENT_ID = "evt-001";
  private static final String CONSUMER = "ApprovalResultEventListener";
  private static final String INSTANCE_ID = "inst-001";
  private static final String BUSINESS_NO = "app-001";

  @Mock
  private FlowOrchestrationService orchestrationService;
  @Mock
  private EventReceiptGateway eventReceiptGateway;
  @Mock
  private CallbackCorrelationService callbackCorrelationService;

  @InjectMocks
  private ApprovalResultEventListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(eventReceiptGateway.tryConsume(any(), any())).thenReturn(true);
    lenient().when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.noCorrelation());
  }

  private ApprovalResultMessage message(ApprovalResult result) {
    return message(result, null);
  }

  private ApprovalResultMessage message(ApprovalResult result, String requestNo) {
    return new ApprovalResultMessage(EVENT_ID, INSTANCE_ID, BUSINESS_NO, requestNo, result);
  }

  @Test
  @DisplayName("审批通过消息应调用 advanceByApprovalResult 传入 APPROVED")
  void handleApproved_shouldAdvanceWithApprovedResult() {
    listener.handleApprovalResult(message(ApprovalResult.APPROVED));

    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "APPROVED");
  }

  @Test
  @DisplayName("审批驳回消息应调用 advanceByApprovalResult 传入 REJECTED")
  void handleRejected_shouldAdvanceWithRejectedResult() {
    listener.handleApprovalResult(message(ApprovalResult.REJECTED));

    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "REJECTED");
  }

  @Test
  @DisplayName("审批撤回消息应调用 advanceByApprovalResult 传入 WITHDRAWN")
  void handleWithdrawn_shouldAdvanceWithWithdrawnResult() {
    listener.handleApprovalResult(message(ApprovalResult.WITHDRAWN));

    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "WITHDRAWN");
  }

  @Test
  @DisplayName("Spring @EventListener 入口应委托给 handleApprovalResult")
  void onApprovalResult_shouldDelegateToHandler() {
    listener.onApprovalResult(message(ApprovalResult.APPROVED));

    verify(orchestrationService).advanceByApprovalResult(BUSINESS_NO, "APPROVED");
  }

  @Test
  @DisplayName("重复事件经消费回执短路，不重复推进流程")
  void handleApproved_whenDuplicate_shouldNotAdvance() {
    when(eventReceiptGateway.tryConsume(EVENT_ID, CONSUMER)).thenReturn(false);

    listener.handleApprovalResult(message(ApprovalResult.APPROVED));

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("APPROVED 关联命中(APPLIED)：用权威 appId 完成等待步骤，不重跑管线")
  void handleApproved_whenApplied_shouldCompleteWaitingStep() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.applied(new ApplicationId("APP-9")));

    listener.handleApprovalResult(message(ApprovalResult.APPROVED, "SE-9"));

    verify(orchestrationService).completeWaitingStep(new ApplicationId("APP-9"));
    verify(orchestrationService, org.mockito.Mockito.never()).advanceByApprovalResult(any(), any());
  }

  @Test
  @DisplayName("REJECTED 关联命中(APPLIED)：保持 advanceByApprovalResult 终止语义")
  void handleRejected_whenApplied_shouldStillTerminate() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.applied(new ApplicationId("APP-9")));

    listener.handleApprovalResult(message(ApprovalResult.REJECTED, "SE-9"));

    verify(orchestrationService).advanceByApprovalResult("APP-9", "REJECTED");
    verify(orchestrationService, org.mockito.Mockito.never()).completeWaitingStep(any());
  }

  @Test
  @DisplayName("关联分类为 OUT_OF_ORDER：审批回调不盲目推进")
  void handleApproved_whenOutOfOrder_shouldNotAdvance() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.outOfOrder());

    listener.handleApprovalResult(message(ApprovalResult.APPROVED, "SE-9"));

    verifyNoInteractions(orchestrationService);
  }
}
