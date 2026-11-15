package com.example.core.adapter.inbound;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.core.application.engine.inbound.ApprovalResultMessage;
import com.example.core.application.engine.inbound.ApprovalResultMessage.ApprovalResult;
import com.example.core.application.engine.inbound.FileParsedMessage;
import com.example.core.application.engine.inbound.OutboundFileGeneratedMessage;
import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 外部集成事件入站适配器测试：外部 DTO → kernel 自有消息。
 */
@DisplayName("ExternalEventInboundAdapter 外部事件映射")
@ExtendWith(MockitoExtension.class)
class ExternalEventInboundAdapterTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 20, 0);

  @Mock
  private ApplicationEventPublisher publisher;

  private ExternalEventInboundAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ExternalEventInboundAdapter(publisher);
  }

  @Test
  @DisplayName("文件解析事件映射保留 requestNo 与子任务统计")
  void onFileParsed_mapsToKernelMessage() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      "evt-1", "task-1", "FORM", "PARTIAL", 2,
      List.of(
        new FileParsedEventDTO.SubTaskSummaryDTO("ST-1", "PLAN-1", 10, 10, 0, "SUCCESS"),
        new FileParsedEventDTO.SubTaskSummaryDTO("ST-2", "PLAN-2", 10, 7, 3, "PARTIAL")),
      null, NOW, "req-1");

    adapter.onFileParsed(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    FileParsedMessage message = (FileParsedMessage) captor.getValue();
    assertThat(message.eventId()).isEqualTo("evt-1");
    assertThat(message.fileTaskId()).isEqualTo("task-1");
    assertThat(message.requestNo()).isEqualTo("req-1");
    assertThat(message.status()).isEqualTo("PARTIAL");
    assertThat(message.subTasks()).hasSize(2);
    assertThat(message.subTasks().get(1).invalidRows()).isEqualTo(3);
  }

  @Test
  @DisplayName("审批通过/驳回/撤回分别映射为统一结果枚举")
  void onApproval_mapsAllResults() {
    adapter.onApproved(new ApprovalInstanceApprovedEventDTO(
      "evt-a", "inst-1", "app-1", "ACC_PLAN_CREATE", NOW, "req-a"));
    adapter.onRejected(new ApprovalInstanceRejectedEventDTO(
      "evt-r", "inst-1", "app-1", "ACC_PLAN_CREATE", NOW, "req-r"));
    adapter.onWithdrawn(new ApprovalInstanceWithdrawnEventDTO(
      "evt-w", "inst-1", "app-1", "ACC_PLAN_CREATE", NOW, "req-w"));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
      .extracting(e -> ((ApprovalResultMessage) e).result())
      .containsExactly(ApprovalResult.APPROVED, ApprovalResult.REJECTED, ApprovalResult.WITHDRAWN);
    assertThat(((ApprovalResultMessage) captor.getAllValues().get(0)).requestNo()).isEqualTo("req-a");
  }

  @Test
  @DisplayName("出站文件生成事件映射为生成文件数量")
  void onOutboundFileGenerated_mapsCount() {
    OutboundFileGeneratedEventDTO event = new OutboundFileGeneratedEventDTO(
      "evt-o", "task-o", "TPL-1",
      List.of(new OutboundFileGeneratedEventDTO.GeneratedFileInfoDTO("ST-1", "PLAN-1", "OUT-1")),
      NOW, "req-o");

    adapter.onOutboundFileGenerated(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    OutboundFileGeneratedMessage message = (OutboundFileGeneratedMessage) captor.getValue();
    assertThat(message.requestNo()).isEqualTo("req-o");
    assertThat(message.generatedFileCount()).isEqualTo(1);
  }
}
