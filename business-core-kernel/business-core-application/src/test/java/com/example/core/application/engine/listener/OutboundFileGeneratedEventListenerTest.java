package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.types.ApplicationId;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 出站文件生成回调监听器：关联服务接入与回退/分类路径（阶段 C）。 */
@DisplayName("出站文件生成事件监听器测试")
@ExtendWith(MockitoExtension.class)
class OutboundFileGeneratedEventListenerTest {

  private static final String EVENT_ID = "evt-001";
  private static final String CONSUMER = "OutboundFileGeneratedEventListener";
  private static final String FILE_TASK_ID = "task-001";
  private static final String TEMPLATE_ID = "TPL-1";
  private static final LocalDateTime OCCURRED_ON = LocalDateTime.now();

  @Mock
  private FlowOrchestrationService orchestrationService;
  @Mock
  private EventReceiptGateway eventReceiptGateway;
  @Mock
  private CallbackCorrelationService callbackCorrelationService;

  @InjectMocks
  private OutboundFileGeneratedEventListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(eventReceiptGateway.tryConsume(any(), any())).thenReturn(true);
    lenient().when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.noCorrelation());
  }

  private OutboundFileGeneratedEventDTO legacyEvent() {
    return new OutboundFileGeneratedEventDTO(
      EVENT_ID, FILE_TASK_ID, TEMPLATE_ID, List.of(), OCCURRED_ON);
  }

  @Test
  @DisplayName("旧构造（无 requestNo）NO_CORRELATION：回退 advanceByFileTaskId")
  void onOutbound_whenNoCorrelation_shouldLegacyAdvance() {
    listener.onOutboundFileGenerated(legacyEvent());

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("关联命中 APPLIED：用权威 appId 推进流程")
  void onOutbound_whenApplied_shouldAdvanceByApplicationId() {
    ApplicationId appId = new ApplicationId("APP-9");
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.applied(appId));
    OutboundFileGeneratedEventDTO event = new OutboundFileGeneratedEventDTO(
      EVENT_ID, FILE_TASK_ID, TEMPLATE_ID, List.of(), OCCURRED_ON, "SE-9");

    listener.onOutboundFileGenerated(event);

    verify(orchestrationService).advanceStep(appId);
    verify(orchestrationService, never()).advanceByFileTaskId(any());
  }

  @Test
  @DisplayName("UNMATCHED 分类：不盲目推进")
  void onOutbound_whenUnmatched_shouldNotAdvance() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.unmatched());
    OutboundFileGeneratedEventDTO event = new OutboundFileGeneratedEventDTO(
      EVENT_ID, FILE_TASK_ID, TEMPLATE_ID, List.of(), OCCURRED_ON, "SE-9");

    listener.onOutboundFileGenerated(event);

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("重复事件经消费回执短路，不重复推进")
  void onOutbound_whenDuplicate_shouldNotAdvance() {
    when(eventReceiptGateway.tryConsume(EVENT_ID, CONSUMER)).thenReturn(false);

    listener.onOutboundFileGenerated(legacyEvent());

    verifyNoInteractions(orchestrationService);
  }
}
