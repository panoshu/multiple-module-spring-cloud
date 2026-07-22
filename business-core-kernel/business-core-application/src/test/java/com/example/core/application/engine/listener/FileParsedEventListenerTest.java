package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.file.api.event.FileParsedEventDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FileParsedEventListener 单元测试
 * <p>
 * 验证文件解析完成集成事件监听器的核心行为：
 * <ul>
 * <li>SUCCESS 状态调用 advanceByFileTaskId 推进流程</li>
 * <li>PARTIAL 状态调用 advanceByFileTaskId 推进流程</li>
 * <li>FAILED 状态不调用 advanceByFileTaskId，仅记录日志</li>
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
  private static final String BIZ_TYPE = "FORM_DETAIL";
  private static final LocalDateTime OCCURRED_ON = LocalDateTime.now();

  @Mock
  private FlowOrchestrationService orchestrationService;

  @InjectMocks
  private FileParsedEventListener listener;

  @Test
  @DisplayName("解析状态为 SUCCESS 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusSuccess_shouldAdvanceByFileTaskId() {
    // given: 文件解析成功事件
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      3, Collections.emptyList(), null, OCCURRED_ON
    );

    // when
    listener.handleFileParsed(event);

    // then
    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 PARTIAL 时应调用 advanceByFileTaskId 推进流程")
  void handleFileParsed_whenStatusPartial_shouldAdvanceByFileTaskId() {
    // given: 文件解析部分成功事件
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "PARTIAL",
      3, Collections.emptyList(), null, OCCURRED_ON
    );

    // when
    listener.handleFileParsed(event);

    // then
    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }

  @Test
  @DisplayName("解析状态为 FAILED 时不应调用 advanceByFileTaskId")
  void handleFileParsed_whenStatusFailed_shouldNotAdvanceByFileTaskId() {
    // given: 文件解析失败事件
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "FAILED",
      0, Collections.emptyList(), "文件格式错误", OCCURRED_ON
    );

    // when
    listener.handleFileParsed(event);

    // then: 失败时不推进流程，仅记录日志
    verifyNoInteractions(orchestrationService);
  }

  @Test
  @DisplayName("Spring @EventListener 入口应委托给 handleFileParsed 处理")
  void onFileParsed_shouldDelegateToHandleFileParsed() {
    // given
    FileParsedEventDTO event = new FileParsedEventDTO(
      EVENT_ID, FILE_TASK_ID, BIZ_TYPE, "SUCCESS",
      1, Collections.emptyList(), null, OCCURRED_ON
    );

    // when
    listener.onFileParsed(event);

    // then
    verify(orchestrationService).advanceByFileTaskId(FILE_TASK_ID);
  }
}
