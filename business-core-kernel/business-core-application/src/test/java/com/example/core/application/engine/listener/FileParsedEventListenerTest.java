package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.types.ApplicationId;
import com.example.file.api.event.FileParsedEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
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

  @InjectMocks
  private FileParsedEventListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(eventReceiptGateway.tryConsume(any(), any())).thenReturn(true);
    // 既有事件为旧构造（requestNo 为空），关联服务返回 NO_CORRELATION，走 legacy 路径
    lenient().when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.noCorrelation());
  }

  @Test
  @DisplayName("解析状态为 SUCCESS 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusSuccess_shouldAdvanceByFileTaskId() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      3, Collections.emptyList(), null, OCCURRED_ON
    );

    listener.handleFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 PARTIAL 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusPartial_shouldAdvanceByFileTaskId() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "PARTIAL",
      3, Collections.emptyList(), null, OCCURRED_ON
    );

    listener.handleFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 FAILED 时不应调用 advanceByFileTaskId")
  void handleFileParsed_whenStatusFailed_shouldNotAdvanceByFileTaskId() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "FAILED",
      0, Collections.emptyList(), "文件格式错误", OCCURRED_ON
    );

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("Spring @EventListener 入口应委托给 handleFileParsed 处理")
  void onFileParsed_shouldDelegateToHandleFileParsed() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      1, Collections.emptyList(), null, OCCURRED_ON
    );

    listener.onFileParsed(event);

    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("重复事件经消费回执短路，不重复推进流程")
  void handleFileParsed_whenDuplicate_shouldNotAdvance() {
    when(eventReceiptGateway.tryConsume(EVENT_ID, CONSUMER)).thenReturn(false);
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      1, Collections.emptyList(), null, OCCURRED_ON
    );

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("带 requestNo 且关联命中(APPLIED)：用权威 appId 推进，不再反查 fileTaskId")
  void handleFileParsed_whenApplied_shouldAdvanceByApplicationId() {
    ApplicationId appId = new ApplicationId("APP-9");
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.applied(appId));
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      1, Collections.emptyList(), null, OCCURRED_ON, "SE-9"
    );

    listener.handleFileParsed(event);

    verify(orchestrationService).advanceStep(appId);
    verify(orchestrationService, org.mockito.Mockito.never()).advanceByFileTaskId(any());
  }

  @Test
  @DisplayName("关联分类为 UNMATCHED/OUT_OF_ORDER：不盲目推进流程")
  void handleFileParsed_whenUnmatched_shouldNotAdvance() {
    when(callbackCorrelationService.correlate(any()))
      .thenReturn(CorrelationResult.unmatched());
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      1, Collections.emptyList(), null, OCCURRED_ON, "SE-9"
    );

    listener.handleFileParsed(event);

    verifyNoInteractions(orchestrationService);
  }
}
