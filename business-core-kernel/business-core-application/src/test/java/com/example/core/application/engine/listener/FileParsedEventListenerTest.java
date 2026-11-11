package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
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

  @InjectMocks
  private FileParsedEventListener listener;

  @BeforeEach
  void setUp() {
    lenient().when(eventReceiptGateway.tryConsume(any(), any())).thenReturn(true);
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
}
