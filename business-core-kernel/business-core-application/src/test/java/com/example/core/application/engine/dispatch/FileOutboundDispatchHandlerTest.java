package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FILE_OUTBOUND 派发处理器（从 checkpoint_ref 读载荷，批次 30）。 */
@DisplayName("FileOutboundDispatchHandler 载荷读取与派发")
class FileOutboundDispatchHandlerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);
  private static final StepExecutionId STEP_EXEC_ID = StepExecutionId.of(9L);

  private StepExecutionRepository stepExecutionRepository;
  private FileIntegrationGateway fileGateway;
  private FileOutboundDispatchHandler handler;

  @BeforeEach
  void setUp() {
    stepExecutionRepository = mock(StepExecutionRepository.class);
    fileGateway = mock(FileIntegrationGateway.class);
    handler = new FileOutboundDispatchHandler(stepExecutionRepository, fileGateway);
  }

  @Test
  @DisplayName("supportedType 为 FILE_OUTBOUND")
  void supportedType_isFileOutbound() {
    assertThat(handler.supportedType()).isEqualTo(InteractionType.FILE_OUTBOUND);
  }

  @Test
  @DisplayName("从步骤执行 checkpoint_ref 读载荷并派发，返回 taskId")
  void dispatch_readsPayloadAndReturnsTaskId() {
    ExternalInteraction interaction = interaction();
    when(stepExecutionRepository.load(STEP_EXEC_ID))
      .thenReturn(Optional.of(stepExecution("FT-1\u001FTPL-1")));
    when(fileGateway.triggerOutboundGenerationAndGetTaskId(
      "FT-1", "TPL-1", java.util.Map.of(), "SYSTEM", "SE-9")).thenReturn("TASK-9");

    String taskId = handler.dispatch(interaction);

    assertThat(taskId).isEqualTo("TASK-9");
    verify(fileGateway).triggerOutboundGenerationAndGetTaskId(
      "FT-1", "TPL-1", java.util.Map.of(), "SYSTEM", "SE-9");
  }

  @Test
  @DisplayName("载荷缺失时返回 null、不调网关（保持 DISPATCHING 待对账）")
  void dispatch_returnsNullWhenPayloadMissing() {
    ExternalInteraction interaction = interaction();
    when(stepExecutionRepository.load(STEP_EXEC_ID))
      .thenReturn(Optional.of(stepExecution(null)));

    assertThat(handler.dispatch(interaction)).isNull();

    verify(fileGateway, never()).triggerOutboundGenerationAndGetTaskId(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("步骤执行记录不存在时返回 null、不调网关")
  void dispatch_returnsNullWhenStepExecutionMissing() {
    ExternalInteraction interaction = interaction();
    when(stepExecutionRepository.load(STEP_EXEC_ID)).thenReturn(Optional.empty());

    assertThat(handler.dispatch(interaction)).isNull();

    verify(fileGateway, never()).triggerOutboundGenerationAndGetTaskId(any(), any(), any(), any(), any());
  }

  private ExternalInteraction interaction() {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(1L), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      new ApplicationId("APP-1"), STEP_EXEC_ID, InteractionType.FILE_OUTBOUND,
      "SE-9", null, ExternalInteractionStatus.DISPATCHING,
      null, 0, null, null, null);
  }

  private StepExecution stepExecution(String checkpointRef) {
    return mock(StepExecution.class, invocation -> {
      if (invocation.getMethod().getName().equals("checkpointRef")) {
        return checkpointRef;
      }
      return null;
    });
  }
}
