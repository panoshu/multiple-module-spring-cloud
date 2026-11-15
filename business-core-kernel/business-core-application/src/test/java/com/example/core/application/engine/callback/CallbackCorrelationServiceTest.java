package com.example.core.application.engine.callback;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 阶段 C 回调按 requestNo 关联协作与分类（spec §7.3 / §9.2 阶段 C / §11.4）。 */
@DisplayName("外部协作回调关联服务")
@ExtendWith(MockitoExtension.class)
class CallbackCorrelationServiceTest {

  private static final ApplicationId APP_ID = new ApplicationId("APP-1");
  private static final StepExecutionId STEP_EXEC_ID = StepExecutionId.of(9L);
  private static final String REQUEST_NO = "SE-9";
  private static final String EXTERNAL_TASK_ID = "task-001";
  private static final String OPERATOR = "SYSTEM";
  private static final String RESULT_REF = "file-ref-001";

  @Mock
  private ExternalInteractionRepository externalInteractionRepository;
  @Mock
  private StepExecutionRepository stepExecutionRepository;

  private CallbackCorrelationService service;

  @BeforeEach
  void setUp() {
    service = new CallbackCorrelationService(externalInteractionRepository, stepExecutionRepository);
  }

  private ExternalInteraction interaction(ExternalInteractionStatus status, InteractionType type) {
    ExternalInteraction interaction = mock(ExternalInteraction.class);
    lenient().when(interaction.status()).thenReturn(status);
    lenient().when(interaction.type()).thenReturn(type);
    lenient().when(interaction.applicationId()).thenReturn(APP_ID);
    lenient().when(interaction.stepExecutionId()).thenReturn(STEP_EXEC_ID);
    lenient().when(interaction.matches(REQUEST_NO, EXTERNAL_TASK_ID)).thenReturn(true);
    return interaction;
  }

  private StepExecution stepExecution(StepExecutionState state) {
    StepExecution execution = mock(StepExecution.class);
    lenient().when(execution.state()).thenReturn(state);
    return execution;
  }

  private CallbackCommand command(CallbackOutcome outcome) {
    return new CallbackCommand(REQUEST_NO, EXTERNAL_TASK_ID, InteractionType.FILE_PARSE,
      outcome, RESULT_REF, OPERATOR);
  }

  @Test
  @DisplayName("命中 WAITING_CALLBACK 协作且成功：协作 SUCCEEDED、步骤 SUCCEEDED、返回 APPLIED")
  void correlate_appliedOnSuccess() {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.WAITING_CALLBACK,
      InteractionType.FILE_PARSE);
    StepExecution execution = stepExecution(StepExecutionState.WAITING_EXTERNAL);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));
    when(stepExecutionRepository.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    assertThat(result.applicationId()).isEqualTo(APP_ID);
    verify(interaction).succeed(RESULT_REF, OPERATOR);
    verify(execution).succeed(OPERATOR);
    verify(externalInteractionRepository).save(interaction);
    verify(stepExecutionRepository).save(execution);
  }

  @Test
  @DisplayName("失败结果：协作与步骤进入 FAILED、返回 APPLIED")
  void correlate_appliedOnFailure() {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.DISPATCHING,
      InteractionType.FILE_PARSE);
    StepExecution execution = stepExecution(StepExecutionState.WAITING_EXTERNAL);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));
    when(stepExecutionRepository.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = service.correlate(command(CallbackOutcome.FAILED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    verify(interaction).fail(any(), org.mockito.ArgumentMatchers.eq(OPERATOR));
    verify(execution).fail(any(), any(), org.mockito.ArgumentMatchers.eq(OPERATOR));
  }

  @Test
  @DisplayName("requestNo 为空：回退既有路径 NO_CORRELATION，不访问仓储")
  void correlate_noRequestNo_returnsLegacyFallback() {
    CallbackCommand cmd = new CallbackCommand(null, EXTERNAL_TASK_ID,
      InteractionType.FILE_PARSE, CallbackOutcome.SUCCEEDED, RESULT_REF, OPERATOR);

    CorrelationResult result = service.correlate(cmd);

    assertThat(result.classification()).isEqualTo(CorrelationClassification.NO_CORRELATION);
    verifyNoInteractions(externalInteractionRepository, stepExecutionRepository);
  }

  @Test
  @DisplayName("找不到协作：UNMATCHED 可对账，不盲目推进")
  void correlate_noMatchingInteraction_returnsUnmatched() {
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.empty());
    when(externalInteractionRepository.findByExternalTaskId(InteractionType.FILE_PARSE, EXTERNAL_TASK_ID))
      .thenReturn(Optional.empty());

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.UNMATCHED);
    verify(externalInteractionRepository, never()).save(any());
    verify(stepExecutionRepository, never()).save(any());
  }

  @Test
  @DisplayName("协作类型不符：OUT_OF_ORDER 分类，不推进")
  void correlate_typeMismatch_returnsOutOfOrder() {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.WAITING_CALLBACK,
      InteractionType.APPROVAL);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.OUT_OF_ORDER);
    verify(externalInteractionRepository, never()).save(any());
  }

  @Test
  @DisplayName("协作已终态：OUT_OF_ORDER 分类，不重复推进")
  void correlate_terminalInteraction_returnsOutOfOrder() {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.SUCCEEDED,
      InteractionType.FILE_PARSE);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.OUT_OF_ORDER);
    verify(externalInteractionRepository, never()).save(any());
  }

  @Test
  @DisplayName("按 requestNo 未命中但按 externalTaskId 命中：仍可关联")
  void correlate_fallbackByExternalTaskId() {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.WAITING_CALLBACK,
      InteractionType.FILE_PARSE);
    StepExecution execution = stepExecution(StepExecutionState.WAITING_EXTERNAL);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.empty());
    when(externalInteractionRepository.findByExternalTaskId(InteractionType.FILE_PARSE, EXTERNAL_TASK_ID))
      .thenReturn(Optional.of(interaction));
    when(stepExecutionRepository.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    verify(interaction).succeed(RESULT_REF, OPERATOR);
  }

  @Test
  @DisplayName("matches 为假（乱序/串单）：OUT_OF_ORDER 分类")
  void correlate_matchesFalse_returnsOutOfOrder() {
    ExternalInteraction interaction = mock(ExternalInteraction.class);
    when(interaction.status()).thenReturn(ExternalInteractionStatus.WAITING_CALLBACK);
    when(interaction.type()).thenReturn(InteractionType.FILE_PARSE);
    when(interaction.matches(REQUEST_NO, EXTERNAL_TASK_ID)).thenReturn(false);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));

    CorrelationResult result = service.correlate(command(CallbackOutcome.SUCCEEDED));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.OUT_OF_ORDER);
    verify(externalInteractionRepository, never()).save(any());
  }

  @Test
  @DisplayName("REJECTED / WITHDRAWN：协作与步骤进入 FAILED，不误判为 SUCCEEDED（批次 42）")
  void correlate_rejectedOrWithdrawnMapsToFailed() {
    assertFailureMapped(CallbackOutcome.REJECTED);
    assertFailureMapped(CallbackOutcome.WITHDRAWN);
  }

  @Test
  @DisplayName("outcome 缺失（null）：保守按 FAILED 闭环，不静默当成功")
  void correlate_nullOutcomeMapsToFailed() {
    assertFailureMapped(null);
  }

  private void assertFailureMapped(CallbackOutcome outcome) {
    ExternalInteraction interaction = interaction(ExternalInteractionStatus.WAITING_CALLBACK,
      InteractionType.APPROVAL);
    StepExecution execution = stepExecution(StepExecutionState.WAITING_EXTERNAL);
    when(externalInteractionRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));
    when(stepExecutionRepository.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = service.correlate(new CallbackCommand(
      REQUEST_NO, EXTERNAL_TASK_ID, InteractionType.APPROVAL, outcome, RESULT_REF, OPERATOR));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    verify(interaction).fail(RESULT_REF, OPERATOR);
    verify(interaction, never()).succeed(any(), any());
    verify(execution).fail(any(), any(), org.mockito.ArgumentMatchers.eq(OPERATOR));
    verify(externalInteractionRepository).save(interaction);
    verify(stepExecutionRepository).save(execution);
  }
}
