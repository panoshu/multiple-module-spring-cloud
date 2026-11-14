package com.example.core.application.engine.acceptance;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationClassification;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.dispatch.ExternalInteractionDispatcher;
import com.example.core.application.engine.dispatch.InteractionDispatchHandler;
import com.example.core.application.engine.listener.FileParsedEventListener;
import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.file.api.event.FileParsedEventDTO;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.core.application.engine.acceptance.ExternalCollaborationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §21.3 外部协作验收（批次 39-1）。
 * <p>
 * 使用真实领域聚合 + mock 仓储/网关，把分散在各单测里的认领、派发、回调、恢复能力
 * 串成「故障 → 状态 → 恢复 → 闭环」的完整链路，逐条锁定 §21.3 六条验收标准。
 * 纯新增测试，不改生产代码、不改 annuity。
 * <p>
 * 分工约定：{@code requestNo} 用于关联协作记录，{@code externalTaskId} 用于查询外部系统；
 * 上游「部分成功不被误判为全部成功」由 file-service {@code ParseTaskTest} 的
 * {@code PARTIAL_SUCCESS}/{@code invalidCount} 断言保证，本类覆盖 kernel 消费侧分流。
 */
@DisplayName("§21.3 外部协作验收")
class ExternalCollaborationAcceptanceTest {

  @Test
  @DisplayName("明确失败：协作与步骤进入 FAILED，且可转入 RETRY_WAIT 重派")
  void explicitFailure_closesToFailedAndCanRetry() {
    ExternalInteractionRepository interactionRepo = mock(ExternalInteractionRepository.class);
    StepExecutionRepository stepRepo = mock(StepExecutionRepository.class);
    CallbackCorrelationService service = new CallbackCorrelationService(interactionRepo, stepRepo);

    ExternalInteraction interaction = interaction(1L, InteractionType.FILE_PARSE, REQUEST_NO,
      TASK_ID, ExternalInteractionStatus.WAITING_CALLBACK);
    StepExecution execution = waitingExternal();
    when(interactionRepo.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));
    when(stepRepo.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = service.correlate(new CallbackCommand(
      REQUEST_NO, TASK_ID, InteractionType.FILE_PARSE, CallbackOutcome.FAILED,
      "解析失败", OPERATOR));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.FAILED);
    assertThat(execution.state()).isEqualTo(StepExecutionState.FAILED);
    verify(interactionRepo).save(interaction);
    verify(stepRepo).save(execution);

    // FAILED 属可重试态：沿用原 requestNo，重试次数递增，重派回 DISPATCHING
    assertThat(interaction.status().canTransitTo(ExternalInteractionStatus.RETRY_WAIT)).isTrue();
    interaction.scheduleRetry(NOW.plusMinutes(5), OPERATOR);
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.RETRY_WAIT);
    assertThat(interaction.retryCount()).isEqualTo(1);
    interaction.dispatchAgain(OPERATOR);
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    assertThat(interaction.requestNo()).isEqualTo(REQUEST_NO);
  }

  @Test
  @DisplayName("请求超时：滞留 DISPATCHING 进入 UNKNOWN，未经对账禁止直接重派")
  void timeout_entersUnknown_andRequiresReconciliation() {
    ExternalInteractionRepository repo = mock(ExternalInteractionRepository.class);
    ApprovalIntegrationGateway gateway = mock(ApprovalIntegrationGateway.class);
    ExternalInteractionRecoveryService service = recoveryService(repo, gateway);

    ExternalInteraction stale = interaction(2L, InteractionType.APPROVAL, REQUEST_NO,
      APPROVAL_INSTANCE_ID, ExternalInteractionStatus.DISPATCHING);
    when(repo.findStaleDispatching(any())).thenReturn(List.of(stale));

    assertThat(service.reclaimStaleDispatching(NOW.minusMinutes(30))).isEqualTo(1);
    assertThat(stale.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);
    assertThat(stale.lastError()).isNotBlank();
    verify(repo).save(stale);

    assertThatThrownBy(() -> stale.dispatchAgain(OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> stale.scheduleRetry(NOW.plusMinutes(5), OPERATOR))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("响应丢失：补发回调按 requestNo 命中 UNKNOWN 协作并闭环")
  void lostResponse_reconcilesByRequestNo() {
    ExternalInteractionRepository interactionRepo = mock(ExternalInteractionRepository.class);
    StepExecutionRepository stepRepo = mock(StepExecutionRepository.class);
    CallbackCorrelationService correlation = new CallbackCorrelationService(interactionRepo, stepRepo);

    ExternalInteraction unknown = interaction(3L, InteractionType.APPROVAL, REQUEST_NO,
      APPROVAL_INSTANCE_ID, ExternalInteractionStatus.UNKNOWN);
    StepExecution execution = waitingExternal();
    when(interactionRepo.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(unknown));
    when(stepRepo.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CorrelationResult result = correlation.correlate(new CallbackCommand(
      REQUEST_NO, APPROVAL_INSTANCE_ID, InteractionType.APPROVAL, CallbackOutcome.SUCCEEDED,
      "approval:APPROVED", OPERATOR));

    assertThat(result.classification()).isEqualTo(CorrelationClassification.APPLIED);
    assertThat(result.applicationId()).isEqualTo(APP_ID);
    assertThat(unknown.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    assertThat(execution.state()).isEqualTo(StepExecutionState.SUCCEEDED);
    verify(interactionRepo).save(unknown);
    verify(stepRepo).save(execution);
  }

  @Test
  @DisplayName("服务重启：仅凭持久化 PENDING_DISPATCH 记录恢复派发至 WAITING_CALLBACK")
  void restart_recoversPendingDispatchFromPersistedState() {
    ExternalInteractionRepository repo = mock(ExternalInteractionRepository.class);
    ExternalInteraction persisted = interaction(4L, InteractionType.FILE_PARSE, REQUEST_NO,
      null, ExternalInteractionStatus.PENDING_DISPATCH);
    when(repo.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH)).thenReturn(List.of(persisted));

    InteractionDispatchHandler handler = mock(InteractionDispatchHandler.class);
    when(handler.supportedType()).thenReturn(InteractionType.FILE_PARSE);
    when(handler.dispatch(any())).thenReturn(TASK_ID);
    ExternalInteractionDispatcher dispatcher = new ExternalInteractionDispatcher(repo, List.of(handler));

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);

    assertThat(persisted.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(persisted.externalTaskId()).isEqualTo(TASK_ID);
  }

  @Test
  @DisplayName("服务重启：认领后崩溃的 DISPATCHING 记录回收为 UNKNOWN 并对账确认")
  void restart_reclaimsStaleDispatchingForReconciliation() {
    ExternalInteractionRepository repo = mock(ExternalInteractionRepository.class);
    ApprovalIntegrationGateway gateway = mock(ApprovalIntegrationGateway.class);
    ExternalInteractionRecoveryService service = recoveryService(repo, gateway);

    ExternalInteraction stale = interaction(5L, InteractionType.APPROVAL, "SE-10",
      APPROVAL_INSTANCE_ID, ExternalInteractionStatus.DISPATCHING);
    when(repo.findStaleDispatching(any())).thenReturn(List.of(stale));
    assertThat(service.reclaimStaleDispatching(NOW.minusMinutes(30))).isEqualTo(1);
    assertThat(stale.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);

    when(repo.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(stale));
    when(gateway.queryApprovalStatus(APPROVAL_INSTANCE_ID)).thenReturn("APPROVED");
    assertThat(service.reconcileUnknown()).isEqualTo(1);
    assertThat(stale.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
  }

  @Test
  @DisplayName("审批通过/驳回/撤回分别进入正确协作状态（含撤回缺口）")
  void approvalPassRejectWithdraw_routeToCorrectState() {
    Map<String, ExternalInteractionStatus> expected = Map.of(
      "APPROVED", ExternalInteractionStatus.SUCCEEDED,
      "REJECTED", ExternalInteractionStatus.FAILED,
      "WITHDRAWN", ExternalInteractionStatus.FAILED);
    long id = 10L;
    for (Map.Entry<String, ExternalInteractionStatus> entry : expected.entrySet()) {
      ExternalInteractionRepository repo = mock(ExternalInteractionRepository.class);
      ApprovalIntegrationGateway gateway = mock(ApprovalIntegrationGateway.class);
      ExternalInteractionRecoveryService service = recoveryService(repo, gateway);
      ExternalInteraction unknown = interaction(id++, InteractionType.APPROVAL, REQUEST_NO,
        APPROVAL_INSTANCE_ID, ExternalInteractionStatus.UNKNOWN);
      when(repo.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(unknown));
      when(gateway.queryApprovalStatus(APPROVAL_INSTANCE_ID)).thenReturn(entry.getKey());

      assertThat(service.reconcileUnknown()).isEqualTo(1);
      assertThat(unknown.status()).as("外部状态 %s", entry.getKey()).isEqualTo(entry.getValue());
      verify(repo).save(unknown);
    }
  }

  @Test
  @DisplayName("文件部分成功：PARTIAL 保留失败明细且只推进一次，FAILED 不推进")
  void partialFileStatus_routesWithoutOvercounting() {
    FlowOrchestrationService orchestration = mock(FlowOrchestrationService.class);
    EventReceiptGateway receipt = mock(EventReceiptGateway.class);
    CallbackCorrelationService correlation = mock(CallbackCorrelationService.class);
    when(receipt.tryConsume(any(), any())).thenReturn(true);
    when(correlation.correlate(any())).thenReturn(CorrelationResult.noCorrelation());
    FileParsedEventListener listener = new FileParsedEventListener(orchestration, receipt, correlation);

    // 2 个子任务：一个全成功、一个含 3 行无效；顶层 status=PARTIAL 而非 SUCCESS
    List<FileParsedEventDTO.SubTaskSummaryDTO> subTasks = List.of(
      new FileParsedEventDTO.SubTaskSummaryDTO("ST-1", "K1", 10, 10, 0, "SUCCESS"),
      new FileParsedEventDTO.SubTaskSummaryDTO("ST-2", "K2", 10, 7, 3, "PARTIAL"));
    FileParsedEventDTO partial = new FileParsedEventDTO(
      "evt-partial", TASK_ID, "FORM_DETAIL", "PARTIAL", 2, subTasks, null, NOW);
    FileParsedEventDTO failed = new FileParsedEventDTO(
      "evt-failed", TASK_ID, "FORM_DETAIL", "FAILED", 0, List.of(), "格式错误", NOW);

    listener.handleFileParsed(partial);
    listener.handleFileParsed(failed);

    // 失败明细未被折叠成「全部成功」，且 PARTIAL 只推进一次、FAILED 不推进
    assertThat(partial.subTasks()).extracting(FileParsedEventDTO.SubTaskSummaryDTO::invalidRows)
      .containsExactly(0, 3);
    verify(orchestration, times(1)).advanceByFileTaskId(TASK_ID);

    ArgumentCaptor<CallbackCommand> commands = ArgumentCaptor.forClass(CallbackCommand.class);
    verify(correlation, times(2)).correlate(commands.capture());
    assertThat(commands.getAllValues().get(0).outcome()).isEqualTo(CallbackOutcome.SUCCEEDED);
    assertThat(commands.getAllValues().get(1).outcome()).isEqualTo(CallbackOutcome.FAILED);
    // 端到端对账：PARTIAL 的失败明细统计随 resultRef 写入协作记录，不折叠成「全部成功」
    assertThat(commands.getAllValues().get(0).resultRef())
      .isEqualTo("PARTIAL:subTasks=2,failed=1,rows=20,valid=17,invalid=3");
  }

  /** 恢复服务现按单条包事务，测试侧提供直通的事务管理器。 */
  private ExternalInteractionRecoveryService recoveryService(
    ExternalInteractionRepository repo, ApprovalIntegrationGateway gateway) {
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return new ExternalInteractionRecoveryService(repo, gateway, txManager);
  }
}
