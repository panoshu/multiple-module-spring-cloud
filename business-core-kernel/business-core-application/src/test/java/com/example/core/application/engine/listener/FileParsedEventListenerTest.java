package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.types.ApplicationId;
import com.example.core.types.FormId;
import com.example.core.application.engine.inbound.FileParsedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FileParsedEventListener 单元测试
 * <p>
 * 验证文件解析完成集成事件监听器的核心行为：
 * <ul>
 * <li>SUCCESS 状态调用 advanceByFileTaskId 推进流程</li>
 * <li>PARTIAL 状态调用 advanceByFileTaskId 推进流程</li>
 * <li>FAILED 状态不调用 advanceByFileTaskId，仅记录日志</li>
 * <li>重复 eventId 经消费回执短路，不重复推进</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@DisplayName("FileParsedEventListener 文件解析完成事件监听器测试")
@ExtendWith(MockitoExtension.class)
class FileParsedEventListenerTest {

  private static final String FILE_TASK_ID = "task-001";
  private static final String EVENT_ID = "evt-001";
  private static final String CONSUMER = "FileParsedEventListener";
  private static final String BIZ_TYPE = "FORM_DETAIL";
  private static final LocalDateTime OCCURRED_ON = LocalDateTime.now();

  @Mock
  private FlowOrchestrationService orchestrationService;
  @Mock
  private EventReceiptGateway eventReceiptGateway;
  @Mock
  private CallbackCorrelationService callbackCorrelationService;
  @Mock
  private BusinessFormAppService formAppService;
  @Mock
  private FormRepository formRepository;

  @InjectMocks
  private FileParsedEventListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(eventReceiptGateway.tryConsume(any(), any())).thenReturn(true);
    // 既有事件为旧构造（requestNo 为空），关联服务返回 NO_CORRELATION，走 legacy 路径
    lenient().when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.noCorrelation());
    lenient().when(formRepository.load(any())).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("requestNo 命中表单：按 subTasks 裂变 BusinessApplication，不走申请单等待步骤")
  void handleFileParsed_formLevel_spawnsApplications() {
    FormId formId = new FormId("FORM-1");
    BusinessForm form = org.mockito.Mockito.mock(BusinessForm.class);
    when(form.id()).thenReturn(formId);
    when(formRepository.load(formId)).thenReturn(Optional.of(form));

    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, "FORM-1", "SUCCESS", 1, List.of(new FileParsedMessage.ParsedSubTaskMessage(
        "SUB-1", "PLAN-1", 2, 2, 0, "VALID")), null);

    listener.handleFileParsed(event);

    verify(formAppService).handleParsingResult(eq(formId), argThat(results ->
      results.size() == 1
        && "SUB-1".equals(results.get(0).subTaskId())
        && "PLAN-1".equals(results.get(0).planId().value())));
    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("解析状态为 SUCCESS 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusSuccess_shouldAdvanceByFileTaskId() {
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, null, "SUCCESS", 3, Collections.emptyList(), null);

    listener.handleFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 PARTIAL 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusPartial_shouldAdvanceByFileTaskId() {
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, null, "PARTIAL", 3, Collections.emptyList(), null);

    listener.handleFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 FAILED 时不应调用 advanceByFileTaskId")
  void handleFileParsed_whenStatusFailed_shouldNotAdvanceByFileTaskId() {
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, null, "FAILED", 0, Collections.emptyList(), "文件格式错误");

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("Spring @EventListener 入口应委托给 handleFileParsed 处理")
  void onFileParsed_shouldDelegateToHandleFileParsed() {
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, null, "SUCCESS", 1, Collections.emptyList(), null);

    listener.onFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("重复事件经消费回执短路，不重复推进流程")
  void handleFileParsed_whenDuplicate_shouldNotAdvance() {
    when(eventReceiptGateway.tryConsume(EVENT_ID, CONSUMER)).thenReturn(false);
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, null, "SUCCESS", 1, Collections.emptyList(), null);

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("带 requestNo 且关联命中(APPLIED)：用权威 appId 完成等待步骤，不重跑管线")
  void handleFileParsed_whenApplied_shouldCompleteWaitingStep() {
    ApplicationId appId = new ApplicationId("APP-9");
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.applied(appId));
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, "SE-9", "SUCCESS", 1, Collections.emptyList(), null);

    listener.handleFileParsed(event);

    verify(orchestrationService).completeWaitingStep(appId);
    verify(orchestrationService, org.mockito.Mockito.never()).advanceStep(any());
    verify(orchestrationService, org.mockito.Mockito.never()).advanceByFileTaskId(any());
  }

  @Test
  @DisplayName("关联分类为 UNMATCHED/OUT_OF_ORDER：不盲目推进流程")
  void handleFileParsed_whenUnmatched_shouldNotAdvance() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.unmatched());
    FileParsedMessage event = new FileParsedMessage(
      EVENT_ID, FILE_TASK_ID, "SE-9", "SUCCESS", 1, Collections.emptyList(), null);

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }
}
